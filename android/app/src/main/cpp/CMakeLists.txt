cmake_minimum_required(VERSION 3.22.1)
project(scjvmhost C)

add_library(scjvmhost SHARED scjvmhost.c)

find_library(android-log-lib log)
find_library(android-dl-lib dl)

target_link_libraries(scjvmhost ${android-log-lib} ${android-dl-lib})
