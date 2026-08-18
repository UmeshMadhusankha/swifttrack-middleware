cmake_minimum_required(VERSION 3.16)
project(legacy_wms_daemon CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_executable(wms_server src/wms_server.cpp)

target_include_directories(wms_server PRIVATE include)

# std::this_thread::sleep_for needs the pthread library on Linux.
find_package(Threads REQUIRED)
target_link_libraries(wms_server PRIVATE Threads::Threads)
