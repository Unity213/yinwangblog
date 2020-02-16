package com.mocyx.yinwangblog;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author Administrator
 */
@Slf4j
public class ActionHandler extends SimpleChannelInboundHandler<FullHttpRequest> {


    private String fileToContentType(String path) {

        if (path.endsWith(".txt")) {
            return "text/plain; charset=utf-8";
        } else if (path.endsWith(".html")) {
            return "text/html; charset=utf-8";
        } else if (path.endsWith(".png")) {
            return "image/png";
        } else if (path.endsWith(".jpg")) {
            return "image/jpeg";
        } else if (path.endsWith(".css")) {
            return "text/css; charset=utf-8";
        } else {
            return "text/html; charset=utf-8";
        }
    }

    private byte[] readFile(String path) {
        String filePath = Global.webRoot + path;
        Path p = Paths.get(filePath).toAbsolutePath();
        Path pRoot = Paths.get(Global.webRoot).toAbsolutePath();
        if (!p.startsWith(pRoot)) {
            throw new HttpException(HttpResponseStatus.FORBIDDEN, "FORBIDDEN: " + path);
        }

        File f = p.toFile();
        if (!f.exists()) {
            throw new HttpException(HttpResponseStatus.NOT_FOUND, "NOT_FOUND: " + path);
        }
        if (f.isDirectory()) {
            throw new HttpException(HttpResponseStatus.FORBIDDEN, "FORBIDDEN: " + path);
        }

        try {
            byte[] bytes = FileUtils.readFileToByteArray(f);
            return bytes;
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new HttpException(HttpResponseStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR");
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, FullHttpRequest req) throws Exception {
        QueryStringDecoder queryString = new QueryStringDecoder(req.uri());

        String path = queryString.path();

        if (path.endsWith("/")) {
            path = "/index.html";
        }

        try {
            log.info("ActionHandler {}", queryString);
            byte[] data = readFile(path);

            ByteBuf buf = channelHandlerContext.alloc().buffer();
            buf.writeBytes(data);

            FullHttpResponse fullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                    (buf));
            fullHttpResponse.headers().add(HttpHeaderNames.CONTENT_TYPE, fileToContentType(path));
            fullHttpResponse.headers().add(HttpHeaderNames.CONTENT_LENGTH, data.length);

            channelHandlerContext.writeAndFlush(fullHttpResponse);
        } catch (HttpException e) {
            HttpResponseStatus status = e.code;
            String html = String.format("<h1>%d</h1><p>%s</p>", status.code(), e.getMessage());
            byte[] data = html.getBytes();

            ByteBuf buf = channelHandlerContext.alloc().buffer();
            buf.writeBytes(data);
            FullHttpResponse fullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status,
                    buf);
            fullHttpResponse.headers().add(HttpHeaderNames.CONTENT_TYPE, "text/html");
            fullHttpResponse.headers().add(HttpHeaderNames.CONTENT_LENGTH, data.length);
            channelHandlerContext.writeAndFlush(fullHttpResponse);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (cause instanceof IOException) {
            log.warn("{}", cause.getMessage());
        } else {
            super.exceptionCaught(ctx, cause);
        }
    }
}
