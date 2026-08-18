package com.swiftlogistics.wmsadapter.wms;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Speaks the warehouse system's proprietary TCP protocol.
 *
 * The daemon has no HTTP, no JSON and no library to import. A request is 24 raw
 * bytes on a socket:
 *
 *   bytes  0..7   order id, 64-bit signed integer, big-endian
 *   bytes  8..23  command, ASCII, padded with zero bytes
 *
 * The reply is plain ASCII, and the daemon closes the connection once it has
 * sent it. A fresh connection is opened for every command, which is what the
 * daemon expects: it serves one request per connection.
 */
@Component
public class WmsClient {

    private static final Logger log = LoggerFactory.getLogger(WmsClient.class);

    /** Fixed width of the command field, in bytes. */
    private static final int COMMAND_LENGTH = 16;

    /** Generous ceiling on the reply, purely to stop a broken peer filling memory. */
    private static final int MAX_REPLY_BYTES = 256;

    private final String host;
    private final int port;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public WmsClient(@Value("${wms.host}") String host,
                     @Value("${wms.port}") int port,
                     @Value("${wms.connect-timeout-ms}") int connectTimeoutMs,
                     @Value("${wms.read-timeout-ms}") int readTimeoutMs) {
        this.host = host;
        this.port = port;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    /**
     * Sends one command and waits for the acknowledgement.
     *
     * @throws IOException if the warehouse cannot be reached, goes quiet, or
     *                     hangs up early. The caller turns that into a failure
     *                     event rather than letting it escape.
     */
    public WmsAck send(long orderId, String command) throws IOException {
        try (Socket socket = new Socket()) {
            // Two separate clocks: one for getting connected, one for waiting on
            // an answer. Without the second, a daemon that accepts the
            // connection and then hangs would block this thread forever.
            socket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
            socket.setSoTimeout(readTimeoutMs);

            writeRequest(socket.getOutputStream(), orderId, command);
            String reply = readReply(socket.getInputStream());

            log.debug("WMS replied '{}' to {} for order {}", reply, command, orderId);
            return WmsAck.fromWire(reply);
        }
    }

    /** Writes the 24-byte request. */
    private void writeRequest(OutputStream rawStream, long orderId, String command) throws IOException {
        DataOutputStream out = new DataOutputStream(rawStream);

        // writeLong always emits 8 bytes most-significant-first, which is
        // exactly the big-endian layout the daemon decodes.
        out.writeLong(orderId);
        out.write(toPaddedCommand(command));
        out.flush();
    }

    /** Turns "RESERVE" into 16 bytes of ASCII followed by zero padding. */
    private byte[] toPaddedCommand(String command) {
        byte[] ascii = command.getBytes(StandardCharsets.US_ASCII);
        if (ascii.length > COMMAND_LENGTH) {
            throw new IllegalArgumentException(
                    "Command '" + command + "' is longer than " + COMMAND_LENGTH + " bytes");
        }

        // A fresh array is already full of zeros, so copying in the text is all
        // the padding that is needed.
        byte[] padded = new byte[COMMAND_LENGTH];
        System.arraycopy(ascii, 0, padded, 0, ascii.length);
        return padded;
    }

    /**
     * Reads the whole reply.
     *
     * TCP is a stream of bytes, not of messages: one read can return part of the
     * answer and leave the rest for the next one. The protocol marks the end of
     * a reply by closing the connection, so we keep reading until the stream
     * ends rather than trusting a single read to have brought everything.
     */
    private String readReply(InputStream in) throws IOException {
        ByteArrayOutputStream reply = new ByteArrayOutputStream();
        byte[] chunk = new byte[64];

        int bytesRead;
        while ((bytesRead = in.read(chunk)) != -1) {
            reply.write(chunk, 0, bytesRead);

            if (reply.size() > MAX_REPLY_BYTES) {
                throw new IOException("WMS reply exceeded " + MAX_REPLY_BYTES + " bytes");
            }
        }

        if (reply.size() == 0) {
            throw new IOException("WMS closed the connection without replying");
        }

        return reply.toString(StandardCharsets.US_ASCII).trim();
    }
}
