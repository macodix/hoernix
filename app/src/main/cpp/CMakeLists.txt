cmake_minimum_required(VERSION 3.22)
project(hoernix_dsp LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# KissFFT — eingebettete Quelle, Herkunft und Prüfsumme: third_party/THIRD_PARTY.md
add_library(kissfft STATIC
        third_party/kissfft/kiss_fft.c
        third_party/kissfft/kiss_fftr.c)
target_include_directories(kissfft PUBLIC third_party/kissfft)

# DSP-Kern — plattformneutral, läuft identisch im Android- und im Host-Build.
add_library(hoernix_dsp_kern STATIC
        dsp/biquad.cpp
        dsp/eq_bank.cpp
        dsp/frequenz_verschiebung.cpp
        dsp/begrenzer.cpp
        dsp/kanal_kette.cpp)
target_include_directories(hoernix_dsp_kern PUBLIC dsp)
target_link_libraries(hoernix_dsp_kern PUBLIC kissfft)

if(ANDROID)
    add_library(hoernix_dsp SHARED hoernix_dsp.cpp)
    target_link_libraries(hoernix_dsp PRIVATE hoernix_dsp_kern)
else()
    # Host-Build: Modultests (aufrufbar über ctest).
    enable_testing()
    add_executable(dsp_tests tests/test_dsp.cpp)
    target_link_libraries(dsp_tests PRIVATE hoernix_dsp_kern)
    add_test(NAME dsp_tests COMMAND dsp_tests)
endif()
