/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jun.flume.netty.http;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.apache.flume.ChannelException;
import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.EventDrivenSource;
import org.apache.flume.conf.Configurable;
import org.apache.flume.instrumentation.SourceCounter;
import org.apache.flume.source.AbstractSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;

import java.io.UnsupportedEncodingException;
import java.util.*;

public class HTTPSource extends AbstractSource implements EventDrivenSource, Configurable {

	private static final Logger LOG = LoggerFactory.getLogger(HTTPSource.class);
	private volatile Integer port;
	private volatile String host;
	private volatile Integer nettyHttpObjectAggregatorMaxLength;
	private volatile ServerBootstrap srv;
	private volatile ChannelFuture f;
	private volatile EventLoopGroup bossGroup;
	private volatile EventLoopGroup workerGroup;
	private volatile Thread t;
	private volatile String charset;
	private NettyHTTPSourceHandler handler;
	private SourceCounter sourceCounter;

	public void configure(Context context) {
		try {
			LOG.info("configing... ");
			port = context.getInteger(HTTPSourceConfigurationConstants.CONFIG_PORT);
			host = context.getString(HTTPSourceConfigurationConstants.CONFIG_BIND, HTTPSourceConfigurationConstants.DEFAULT_BIND);
			checkHostAndPort();
			nettyHttpObjectAggregatorMaxLength = context.getInteger(HTTPSourceConfigurationConstants.CONFIG_MAXLENGTH,
					HTTPSourceConfigurationConstants.DEFAULT_MAXLENGTH);
			charset = context.getString(HTTPSourceConfigurationConstants.CONFIG_CHARSET, HTTPSourceConfigurationConstants.DEFAULT_CHARSET);
			Preconditions.checkState(host != null && !host.isEmpty(), "HTTPSource hostname specified is empty");
			Preconditions.checkNotNull(port, "HTTPSource requires a port number to be" + " specified");

			String handlerClassName = context.getString(HTTPSourceConfigurationConstants.CONFIG_HANDLER, HTTPSourceConfigurationConstants.DEFAULT_HANDLER)
					.trim();
			@SuppressWarnings("unchecked")
			Class<? extends NettyHTTPSourceHandler> clazz = (Class<? extends NettyHTTPSourceHandler>) Class.forName(handlerClassName);
			handler = clazz.getDeclaredConstructor().newInstance();
			Map<String, String> subProps = context.getSubProperties(HTTPSourceConfigurationConstants.CONFIG_HANDLER_PREFIX);
			handler.configure(new Context(subProps));
			LOG.info("configed... ");
		} catch (ClassCastException ex) {
			LOG.error("Deserializer is not an instance of HTTPSourceHandler." + "Deserializer must implement HTTPSourceHandler.");
			Throwables.propagate(ex);
		} catch (Exception ex) {
			LOG.error("Error configuring HTTPSource!", ex);
			Throwables.propagate(ex);
		}
		if (sourceCounter == null) {
			sourceCounter = new SourceCounter(getName());
		}
	}

	private void checkHostAndPort() {
		Preconditions.checkState(host != null && !host.isEmpty(), "HTTPSource hostname specified is empty");
		Preconditions.checkNotNull(port, "HTTPSource requires a port number to be" + " specified");
	}

	@Override
	public void start() {
		Preconditions.checkState(srv == null, "Running HTTP Server found in source: " + getName() + " before I started one." + "Will not attempt to start.");
		bossGroup = new NioEventLoopGroup();
		workerGroup = new NioEventLoopGroup();
		LOG.info("HTTP Server in source:" + getName() + " starting...");
		srv = new ServerBootstrap();

		try {
			srv.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class).childHandler(new ChannelInitializer<SocketChannel>() {
				@Override
				public void initChannel(SocketChannel ch) throws Exception {
					ch.pipeline().addLast(new HttpRequestDecoder());
					ch.pipeline().addLast(new HttpObjectAggregator(nettyHttpObjectAggregatorMaxLength));
					ch.pipeline().addLast(new HttpResponseEncoder());
					ch.pipeline().addLast(new ResponseHandler());
				}
			}).option(ChannelOption.SO_BACKLOG, 128).childOption(ChannelOption.SO_KEEPALIVE, true);

			t = new Thread() {
				@Override
				public void run() {
					try {
						f = srv.bind(host, port).sync();
						f.channel().closeFuture().sync();
					} catch (InterruptedException ex) {
						LOG.warn("Thread interrupted... ");
					} catch (Exception ex) {
						LOG.error("Error while starting HTTPSource. Exception follows.", ex);
						Throwables.propagate(ex);
					} finally {
						workerGroup.shutdownGracefully();
						bossGroup.shutdownGracefully();
					}
				}
			};

			t.start();
			LOG.info("HTTP Server in source:" + getName() + " started...");
		} catch (Exception ex) {
			LOG.error("Error while starting HTTPSource. Exception follows.", ex);
			Throwables.propagate(ex);
		}
		sourceCounter.start();
		super.start();
	}

	@Override
	public void stop() {
		try {
			LOG.debug("stoping... ");
			t.interrupt();
			workerGroup = null;
			bossGroup = null;
			srv = null;
			LOG.debug("stoped...");
		} catch (Exception ex) {
			LOG.error("Error while stopping HTTPSource. Exception follows.", ex);
		}
		sourceCounter.stop();
		LOG.info("Http source {} stopped. Metrics: {}", getName(), sourceCounter);
	}

	private class ResponseHandler extends ChannelInboundHandlerAdapter implements Configurable {
		private HttpRequest request;
		private final Gson gson;

		public ResponseHandler() {
			super();
			gson = new GsonBuilder().create();
		}

		@Override
		public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
			if (msg instanceof HttpRequest) {
				request = (HttpRequest) msg;
			}
			if (msg instanceof HttpContent) {
				HttpContent content = (HttpContent) msg;
				ByteBuf buf = content.content();
				String rev = getMessage(buf);
				LOG.info("{ResponseHandler}:message received: " + rev + ". message length: " + rev.length());

				HttpResponseStatus repStatus = HttpResponseStatus.SERVICE_UNAVAILABLE;

				List<Event> events = Collections.emptyList();
				events = handler.getEvents(rev);
				HashMap<String, String> ht = new HashMap<String, String>();
				String res;
				try {

					sourceCounter.incrementAppendBatchReceivedCount();
					sourceCounter.addToEventReceivedCount(events.size());

					getChannelProcessor().processEventBatch(events);

					ht.put("result", "OK");
					ht.put("massage_number", Integer.toString(events.size()));

					sourceCounter.incrementAppendBatchAcceptedCount();
					sourceCounter.addToEventAcceptedCount(events.size());

				} catch (ChannelException ex) {
					LOG.warn("Error appending event to channel. " + "Channel might be full. Consider increasing the channel "
							+ "capacity or make sure the sinks perform faster.", ex);
					repStatus = HttpResponseStatus.INTERNAL_SERVER_ERROR;
					ht.put("error", "Error appending event to channel. Channel might be full." + ex.getMessage());
					return;
				} catch (Exception ex) {
					LOG.warn("Unexpected error appending event to channel. ", ex);
					repStatus = HttpResponseStatus.BAD_REQUEST;
					ht.put("error", "Unexpected error while appending event to channel. " + ex.getMessage());
					return;
				} finally {
					buf.release();
					res = gson.toJson(ht);
					FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, repStatus, Unpooled.wrappedBuffer(res.getBytes("utf-8")));
					response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
					response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());

					if (HttpUtil.isKeepAlive(request)) {
						response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
					}
					ctx.write(response);
					ctx.flush();
				}

			}
		}

		@Override
		public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
			ctx.flush();
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
			LOG.error(cause.getMessage(), cause);
			ctx.close();
		}

		private String getMessage(ByteBuf buf) {
			byte[] con = new byte[buf.readableBytes()];
			buf.readBytes(con);
			try {
				return new String(con, charset);
			} catch (UnsupportedEncodingException e) {
				LOG.error(e.getMessage(), e);
				return null;
			}
		}

		public void configure(Context context) {
			// TODO Auto-generated method stub

		}
	}

}
