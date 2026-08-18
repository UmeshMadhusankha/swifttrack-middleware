# Stage 1: compile the daemon
FROM alpine:3.20 AS build
RUN apk add --no-cache g++ cmake make
WORKDIR /build
COPY CMakeLists.txt .
COPY include ./include
COPY src ./src
RUN cmake -S . -B build -DCMAKE_BUILD_TYPE=Release && cmake --build build

# Stage 2: ship just the binary
FROM alpine:3.20
RUN apk add --no-cache libstdc++
WORKDIR /app
COPY --from=build /build/build/wms_server .
EXPOSE 9090
CMD ["./wms_server"]
