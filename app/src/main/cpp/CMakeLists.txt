cmake_minimum_required(VERSION 3.22)
project(hoernix_dsp LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# KissFFT — eingebettete Quelle, Herkunft und Prüfsumme: third_party/THIRD_PARTY.md
add_library(kissfft STATIC
        third_party/kissfft/kiss_fft.c
        third_party/kissfft/kiss_fftr.c)
target_include_directories(kissfft PUBLIC third_party/kissfft)

add_library(hoernix_dsp SHARED
        hoernix_dsp.cpp)
target_link_libraries(hoernix_dsp PRIVATE kissfft)
