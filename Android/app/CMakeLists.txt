cmake_minimum_required(VERSION 3.18.1)
project("winsysmonitor")

set(CMAKE_CXX_STANDARD 17)

include_directories(libnode/include/node/)

add_library(libnode SHARED IMPORTED)
set_target_properties(libnode PROPERTIES IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/libnode/bin/${ANDROID_ABI}/libnode.so)

add_library(native-lib SHARED src/main/cpp/native-lib.cpp)
target_link_libraries(native-lib libnode ${log-lib})