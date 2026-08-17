#include <iostream>
#include <winsock2.h>
#include <ws2tcpip.h>
#include <windows.h>

// Explicitly instruct the linker to include the Windows Socket Library
#pragma comment(lib, "Ws2_32.lib")

// The proprietary WMS payload struct
struct WmsPayload
{
    long orderId;
    char command[16];
};

int main()
{
    WSADATA wsaData;
    int iResult;

    // 1. Initialize Winsock
    iResult = WSAStartup(MAKEWORD(2, 2), &wsaData);
    if (iResult != 0)
    {
        std::cerr << "WSAStartup failed: " << iResult << std::endl;
        return 1;
    }

    SOCKET ListenSocket = INVALID_SOCKET;
    SOCKET ClientSocket = INVALID_SOCKET;
    struct sockaddr_in address;
    int addrlen = sizeof(address);

    // 2. Create the listening socket (TCP/IPv4)
    ListenSocket = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (ListenSocket == INVALID_SOCKET)
    {
        std::cerr << "Socket creation failed: " << WSAGetLastError() << std::endl;
        WSACleanup();
        return 1;
    }

    // 3. Bind socket to Port 9090
    address.sin_family = AF_INET;
    address.sin_addr.s_addr = INADDR_ANY;
    address.sin_port = htons(9090);

    if (bind(ListenSocket, (struct sockaddr *)&address, sizeof(address)) == SOCKET_ERROR)
    {
        std::cerr << "Bind failed: " << WSAGetLastError() << std::endl;
        closesocket(ListenSocket);
        WSACleanup();
        return 1;
    }

    // 4. Start listening
    if (listen(ListenSocket, SOMAXCONN) == SOCKET_ERROR)
    {
        std::cerr << "Listen failed: " << WSAGetLastError() << std::endl;
        closesocket(ListenSocket);
        WSACleanup();
        return 1;
    }

    std::cout << "Legacy WMS C++ Daemon listening on TCP Port 9090 (Windows)..." << std::endl;

    while (true)
    {
        // 5. Accept incoming connections
        ClientSocket = accept(ListenSocket, (struct sockaddr *)&address, &addrlen);
        if (ClientSocket == INVALID_SOCKET)
        {
            std::cerr << "Accept failed: " << WSAGetLastError() << std::endl;
            continue; // Skip to next loop instead of crashing
        }

        WmsPayload incomingData;

        // 6. Receive byte stream directly into the struct
        int bytesReceived = recv(ClientSocket, (char *)&incomingData, sizeof(WmsPayload), 0);

        if (bytesReceived > 0)
        {
            std::cout << "WMS: Received command '" << incomingData.command
                      << "' for Order ID: " << incomingData.orderId << std::endl;

            // 7. Simulate warehouse processing (Sleep takes milliseconds in Windows)
            Sleep(1000);

            // 8. Send Success ACK
            const char *ack = "WMS_ACK_SUCCESS";
            send(ClientSocket, ack, (int)strlen(ack), 0);
        }

        // 9. Close client connection cleanly
        closesocket(ClientSocket);
    }

    // 10. Global cleanup
    closesocket(ListenSocket);
    WSACleanup();

    return 0;
}