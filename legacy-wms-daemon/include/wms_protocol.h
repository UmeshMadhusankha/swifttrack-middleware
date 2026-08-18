#ifndef WMS_PROTOCOL_H
#define WMS_PROTOCOL_H

#include <cstddef>
#include <cstdint>
#include <string>

/**
 * The wire format spoken by the legacy warehouse system.
 *
 * A request is exactly 24 bytes, with no framing, no length prefix and no
 * terminator:
 *
 *   bytes  0..7   order id, 64-bit signed integer, big-endian
 *   bytes  8..23  command, ASCII, padded with zero bytes
 *
 * The reply is a plain ASCII string with no terminator; the client reads until
 * the daemon closes the connection.
 *
 * Byte order and field widths are stated explicitly rather than left to the
 * compiler's struct layout. The original version wrote a C struct straight onto
 * the socket, which silently changes size between platforms: `long` is 4 bytes
 * on Windows but 8 on Linux and macOS. Big-endian is used because that is the
 * conventional order on a network, and because Java's DataOutputStream writes
 * that way by default, which keeps the WMS adapter simple.
 */
namespace wms {

constexpr std::size_t kOrderIdLength = 8;
constexpr std::size_t kCommandLength = 16;
constexpr std::size_t kRequestLength = kOrderIdLength + kCommandLength;

/** Sent back when the warehouse accepted the command. */
constexpr const char* kAckSuccess = "WMS_ACK_SUCCESS";

/** Sent back when the warehouse rejected it. */
constexpr const char* kAckFailure = "WMS_ACK_FAILURE";

struct Request {
    std::int64_t orderId;
    std::string command;
};

/** Reads an 8-byte big-endian signed integer. */
inline std::int64_t decodeInt64BigEndian(const unsigned char* bytes) {
    std::int64_t value = 0;
    for (std::size_t i = 0; i < kOrderIdLength; ++i) {
        value = (value << 8) | static_cast<std::int64_t>(bytes[i]);
    }
    return value;
}

/** Turns 24 raw bytes into something readable. Assumes the buffer is full. */
inline Request decodeRequest(const unsigned char* buffer) {
    Request request;
    request.orderId = decodeInt64BigEndian(buffer);

    // The command is padded with zero bytes, so stop at the first one.
    const unsigned char* commandBytes = buffer + kOrderIdLength;
    std::size_t length = 0;
    while (length < kCommandLength && commandBytes[length] != '\0') {
        ++length;
    }
    request.command.assign(reinterpret_cast<const char*>(commandBytes), length);

    return request;
}

}  // namespace wms

#endif  // WMS_PROTOCOL_H
