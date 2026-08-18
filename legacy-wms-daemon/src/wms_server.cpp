#include <arpa/inet.h>
#include <netinet/in.h>
#include <signal.h>
#include <sys/socket.h>
#include <unistd.h>

#include <cctype>
#include <cerrno>
#include <chrono>
#include <cstdlib>
#include <cstring>
#include <iostream>
#include <string>
#include <thread>

#include "wms_protocol.h"

/**
 * A stand-in for SwiftLogistics' warehouse management system.
 *
 * It speaks no HTTP, no JSON and no XML: a client opens a TCP connection,
 * writes 24 raw bytes, reads the acknowledgement, and the connection closes.
 * That awkwardness is the point, since it is what the WMS adapter has to hide
 * from the rest of the middleware.
 *
 * Connections are handled one at a time, exactly like the real thing.
 */
namespace {

constexpr int kDefaultPort = 9090;
constexpr int kProcessingDelayMs = 1000;

/** True for "1", "true" or "yes" in any case. */
bool isEnvFlagEnabled(const char* name) {
    const char* value = std::getenv(name);
    if (value == nullptr) {
        return false;
    }

    std::string text(value);
    for (char& character : text) {
        character = static_cast<char>(std::tolower(character));
    }
    return text == "1" || text == "true" || text == "yes";
}

int readPortFromEnv() {
    const char* value = std::getenv("WMS_PORT");
    if (value == nullptr) {
        return kDefaultPort;
    }

    int port = std::atoi(value);
    return (port > 0 && port < 65536) ? port : kDefaultPort;
}

/**
 * Reads exactly `length` bytes.
 *
 * A single recv() can return less than you asked for; TCP is a stream of bytes
 * and does not preserve message boundaries. Looping until the buffer is full is
 * how a 24-byte message is reassembled.
 */
bool readExactly(int socketFd, unsigned char* buffer, std::size_t length) {
    std::size_t received = 0;

    while (received < length) {
        ssize_t chunk = recv(socketFd, buffer + received, length - received, 0);

        if (chunk == 0) {
            return false;  // The client hung up early.
        }
        if (chunk < 0) {
            if (errno == EINTR) {
                continue;  // Interrupted by a signal, not a real error.
            }
            std::cerr << "WMS: recv failed: " << std::strerror(errno) << std::endl;
            return false;
        }

        received += static_cast<std::size_t>(chunk);
    }

    return true;
}

/** Writes the whole buffer, looping because send() may accept only part of it. */
bool sendAll(int socketFd, const char* data, std::size_t length) {
    std::size_t sent = 0;

    while (sent < length) {
        ssize_t chunk = send(socketFd, data + sent, length - sent, 0);

        if (chunk <= 0) {
            if (chunk < 0 && errno == EINTR) {
                continue;
            }
            std::cerr << "WMS: send failed: " << std::strerror(errno) << std::endl;
            return false;
        }

        sent += static_cast<std::size_t>(chunk);
    }

    return true;
}

/** Creates the listening socket, or returns -1 after printing why it could not. */
int createListeningSocket(int port) {
    int listenFd = socket(AF_INET, SOCK_STREAM, 0);
    if (listenFd < 0) {
        std::cerr << "WMS: socket creation failed: " << std::strerror(errno) << std::endl;
        return -1;
    }

    // Without this, restarting the daemon fails for a minute or two while the
    // old socket sits in TIME_WAIT.
    int reuse = 1;
    if (setsockopt(listenFd, SOL_SOCKET, SO_REUSEADDR, &reuse, sizeof(reuse)) < 0) {
        std::cerr << "WMS: setsockopt failed: " << std::strerror(errno) << std::endl;
        close(listenFd);
        return -1;
    }

    struct sockaddr_in address {};
    address.sin_family = AF_INET;
    address.sin_addr.s_addr = INADDR_ANY;
    address.sin_port = htons(static_cast<uint16_t>(port));

    if (bind(listenFd, reinterpret_cast<struct sockaddr*>(&address), sizeof(address)) < 0) {
        std::cerr << "WMS: bind to port " << port << " failed: " << std::strerror(errno) << std::endl;
        close(listenFd);
        return -1;
    }

    if (listen(listenFd, SOMAXCONN) < 0) {
        std::cerr << "WMS: listen failed: " << std::strerror(errno) << std::endl;
        close(listenFd);
        return -1;
    }

    return listenFd;
}

/** Reads one request, pretends to do warehouse work, and acknowledges it. */
void handleConnection(int clientFd, bool forceFailure) {
    unsigned char buffer[wms::kRequestLength];

    if (!readExactly(clientFd, buffer, wms::kRequestLength)) {
        std::cerr << "WMS: incomplete request, dropping connection" << std::endl;
        return;
    }

    wms::Request request = wms::decodeRequest(buffer);
    std::cout << "WMS: received command '" << request.command
              << "' for order id " << request.orderId << std::endl;

    // Stand-in for forklifts, shelves and people.
    std::this_thread::sleep_for(std::chrono::milliseconds(kProcessingDelayMs));

    const char* reply = forceFailure ? wms::kAckFailure : wms::kAckSuccess;
    if (forceFailure) {
        std::cout << "WMS: WMS_FORCE_FAILURE is on, rejecting order "
                  << request.orderId << std::endl;
    }

    sendAll(clientFd, reply, std::strlen(reply));
    std::cout << "WMS: replied " << reply << std::endl;
}

}  // namespace

int main() {
    // Writing to a socket the client already closed raises SIGPIPE, which would
    // kill the process. Ignoring it turns that into an ordinary send() error.
    signal(SIGPIPE, SIG_IGN);

    const int port = readPortFromEnv();
    const bool forceFailure = isEnvFlagEnabled("WMS_FORCE_FAILURE");

    int listenFd = createListeningSocket(port);
    if (listenFd < 0) {
        return 1;
    }

    std::cout << "Legacy WMS daemon listening on TCP port " << port << std::endl;
    std::cout << "WMS: expecting " << wms::kRequestLength << "-byte requests"
              << " (8-byte big-endian order id + 16-byte command)" << std::endl;
    if (forceFailure) {
        std::cout << "WMS: started in FORCED FAILURE mode, every request will be rejected"
                  << std::endl;
    }

    while (true) {
        struct sockaddr_in clientAddress {};
        socklen_t clientAddressLength = sizeof(clientAddress);

        int clientFd = accept(listenFd, reinterpret_cast<struct sockaddr*>(&clientAddress),
                              &clientAddressLength);
        if (clientFd < 0) {
            if (errno == EINTR) {
                continue;
            }
            std::cerr << "WMS: accept failed: " << std::strerror(errno) << std::endl;
            continue;  // Keep serving instead of crashing.
        }

        handleConnection(clientFd, forceFailure);
        close(clientFd);
    }

    close(listenFd);
    return 0;
}
