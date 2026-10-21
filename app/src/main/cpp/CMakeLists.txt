cmake_minimum_required(VERSION 3.22.1)

project(libvmfix C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_C_EXTENSIONS OFF)

add_library(vmfix SHARED libvmfix.c)

target_compile_options(vmfix PRIVATE
    -Os
    -g0
    -fno-unwind-tables
    -fno-asynchronous-unwind-tables
    -ffunction-sections
    -fdata-sections
    -Wall
    -Wextra
)

target_link_options(vmfix PRIVATE
    -Wl,--gc-sections
    -Wl,--build-id=none
    -Wl,-z,noexecstack
    -Wl,-z,relro
    -Wl,-z,now
)

add_custom_command(TARGET vmfix POST_BUILD
    COMMAND ${CMAKE_STRIP} --strip-unneeded $<TARGET_FILE:vmfix>
    COMMENT "Stripping libvmfix.so"
)

get_filename_component(VMFIX_ASSET_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../assets" ABSOLUTE)
set(VMFIX_ABI_DIR "${VMFIX_ASSET_DIR}/libvmfix/${ANDROID_ABI}")
file(MAKE_DIRECTORY "${VMFIX_ABI_DIR}")

add_custom_command(TARGET vmfix POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy
            $<TARGET_FILE:vmfix> ${VMFIX_ABI_DIR}/libvmfix.so
    COMMENT "Copying libvmfix.so into the assets"
)
