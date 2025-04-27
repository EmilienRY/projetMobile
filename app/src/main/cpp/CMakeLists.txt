cmake_minimum_required(VERSION 3.10.2)

# Nom du projet
project("superpixelapp")

# Ajoute la bibliothèque partagée 'superpixelapp' avec le fichier source C++
add_library(
        superpixelapp
        SHARED
        src/main/cpp/CompressionFragment.cpp
)

# Trouve la bibliothèque 'log' et lie-la
find_library(
        log-lib
        log
)

# Lier la bibliothèque 'log' avec notre bibliothèque native
target_link_libraries(
        superpixelapp
        ${log-lib}
)

