#include <jni.h>
#include <android/log.h>
#include <vector>
#include <queue>
#include <cmath>
#include <limits>
#include <map>
#include <sstream>
#include <iostream>
#include "json.hpp"
#include <stdio.h>
#include <algorithm>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <unordered_set>
#include <random>

using json = nlohmann::json;

#define LOG_TAG "SUPERPIXEL_NATIVE"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

void savePaletteToJson(const std::vector<std::vector<int>> &palette, const std::string &filename) {
    json j = json::array();
    for (int i = 0; i < palette.size(); ++i) {
        json color;
        color["id"] = i;
        color["rgb"] = palette[i];
        j.push_back(color);
    }

    std::ofstream file(filename);
    if (file.is_open()) {
        file << j.dump(4);
        file.close();
    } else {
        LOGD("Erreur lors de l'ouverture du fichier JSON");
    }
}


std::vector<std::vector<double>> loadPaletteFromJson(const std::string &filename) {
    std::ifstream file(filename);
    json j;
    file >> j;
    file.close();

    std::vector<std::vector<double>> palette;
    for (const auto &value : j) {
        palette.push_back({value[0], value[1], value[2]});
    }
    return palette;
}

struct Vec2 { int x, y; Vec2(int _x, int _y) : x(_x), y(_y) {} };
struct Vec3 {
    float r, g, b;
    Vec3() : r(0), g(0), b(0) {}
    Vec3(float _r, float _g, float _b) : r(_r), g(_g), b(_b) {}
    Vec3 operator+(const Vec3 &v) const { return Vec3(r + v.r, g + v.g, b + v.b); }
    Vec3 operator-(const Vec3 &v) const { return Vec3(r - v.r, g - v.g, b - v.b); }
    Vec3 operator/(float s) const { return Vec3(r / s, g / s, b / s); }
    Vec3 operator*(float s) const { return Vec3(r * s, g * s, b * s); }
    void operator+=(const Vec3 &v) { r += v.r; g += v.g; b += v.b; }
    float norm() const { return sqrt(r * r + g * g + b * b); }
};

struct SuperPixel {
    std::vector<Vec2> pixels;
    Vec3 meanColor;
};

static inline int clampInt(int v, int lo, int hi) {
    return std::min(hi, std::max(lo, v));
}
static inline float clampF(float v, float lo, float hi) {
    return std::min(hi, std::max(lo, v));
}

void filtreGaussien(const std::vector<float> &in, std::vector<float> &out, int width, int height, int rayon) {

    int ksize = 2 * rayon + 1;
    std::vector<std::vector<float>> kernel(ksize, std::vector<float>(ksize));
    float sigma = rayon / 2.0f;
    float somme = 0.0f;
    for (int i = -rayon; i <= rayon; ++i)
        for (int j = -rayon; j <= rayon; ++j) {
            float v = std::exp(-(i * i + j * j) / (2 * sigma * sigma));
            kernel[i + rayon][j + rayon] = v;
            somme += v;
        }
    for (int i = 0; i < ksize; ++i)
        for (int j = 0; j < ksize; ++j)
            kernel[i][j] /= somme;

    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            float sum = 0, wsum = 0;
            for (int i = -rayon; i <= rayon; ++i) {
                for (int j = -rayon; j <= rayon; ++j) {
                    int ny = clampInt(y + i, 0, height - 1);
                    int nx = clampInt(x + j, 0, width - 1);
                    float w = kernel[i + rayon][j + rayon];
                    sum += in[ny * width + nx] * w;
                    wsum += w;
                }
            }
            out[y * width + x] = sum / wsum;
        }
    }
}

void gradientSobel(const std::vector<float> &img, std::vector<uint8_t> &gradient, int width, int height) {
    int sobelX[3][3] = { {-1, 0, 1}, {-2, 0, 2}, {-1, 0, 1} };
    int sobelY[3][3] = { {-1, -2, -1}, {0, 0, 0}, {1, 2, 1} };
    for (int y = 1; y < height - 1; ++y) {
        for (int x = 1; x < width - 1; ++x) {
            float gx = 0, gy = 0;
            for (int i = -1; i <= 1; ++i)
                for (int j = -1; j <= 1; ++j) {
                    float pix = img[(y + i) * width + (x + j)];
                    gx += pix * sobelX[i + 1][j + 1];
                    gy += pix * sobelY[i + 1][j + 1];
                }
            int g = (int)std::sqrt(gx * gx + gy * gy);
            gradient[y * width + x] = (uint8_t)clampInt(g, 0, 255);
        }
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_superpixelapp_MainFragment_CreationFragment_traiterImageWatershed(
        JNIEnv *env, jobject thiz, jintArray pixels_, jint width, jint height, jint minSize) {

    jint* pixels = env->GetIntArrayElements(pixels_, nullptr);
    if (!pixels) return;

    std::vector<Vec3> imgIn(width * height);
    std::vector<float> grey(width * height);
    std::vector<float> greyFlou(width * height);
    std::vector<uint8_t> gradient(width * height);
    std::vector<std::vector<int>> labels(height, std::vector<int>(width, -1));

    for (int i = 0; i < width * height; i++) {
        int pixel = pixels[i];
        int r = (pixel >> 16) & 0xff;
        int g = (pixel >> 8) & 0xff;
        int b = (pixel) & 0xff;
        imgIn[i] = Vec3(r, g, b);
        grey[i] = 0.3f * r + 0.59f * g + 0.11f * b;
    }

    int rayonGaussien = 1;
    filtreGaussien(grey, greyFlou, width, height, rayonGaussien);

    gradientSobel(greyFlou, gradient, width, height);

    std::vector<Vec2> markers;
    std::vector<std::vector<bool>> visited(height, std::vector<bool>(width, false));
    int threshold = 1;
    for (int y = 1; y < height - 1; y++) {
        for (int x = 1; x < width - 1; x++) {
            if (visited[y][x]) continue;
            int g = gradient[y * width + x];
            if (g < gradient[y * width + (x - 1)] &&
                g < gradient[y * width + (x + 1)] &&
                g < gradient[(y - 1) * width + x] &&
                g < gradient[(y + 1) * width + x]) {
                std::queue<Vec2> q;
                q.push(Vec2(x, y));
                visited[y][x] = true;
                int sumX = x, sumY = y, count = 1;
                while (!q.empty()) {
                    Vec2 curr = q.front(); q.pop();
                    for (auto& d : std::vector<Vec2>{{1,0},{-1,0},{0,1},{0,-1}}) {
                        int nx = curr.x + d.x, ny = curr.y + d.y;
                        if (nx >= 0 && nx < width && ny >= 0 && ny < height && !visited[ny][nx]) {
                            int ng = gradient[ny * width + nx];
                            if (std::abs(ng - g) < threshold) {
                                visited[ny][nx] = true;
                                q.push(Vec2(nx, ny));
                                sumX += nx; sumY += ny; count++;
                            }
                        }
                    }
                }
                markers.emplace_back(sumX / count, sumY / count);
            }
        }
    }

    std::queue<Vec2> q;
    for (int i = 0; i < markers.size(); ++i) {
        int x = markers[i].x, y = markers[i].y;
        if (x >= 0 && x < width && y >= 0 && y < height) {
            labels[y][x] = i;
            q.push(Vec2(x, y));
        }
    }
    int dirs[4][2] = {{0,1},{0,-1},{1,0},{-1,0}};
    while (!q.empty()) {
        Vec2 curr = q.front(); q.pop();
        int label = labels[curr.y][curr.x];
        for (auto& d : dirs) {
            int nx = curr.x + d[0], ny = curr.y + d[1];
            if (nx >= 0 && nx < width && ny >= 0 && ny < height && labels[ny][nx] == -1) {
                labels[ny][nx] = label;
                q.push(Vec2(nx, ny));
            }
        }
    }

    std::vector<SuperPixel> superPixels(markers.size());
    std::vector<int> sizes(markers.size(), 0);
    for (int y = 0; y < height; y++)
        for (int x = 0; x < width; x++) {
            int label = labels[y][x];
            if (label >= 0) {
                superPixels[label].pixels.emplace_back(x, y);
                superPixels[label].meanColor += imgIn[y * width + x];
                sizes[label]++;
            }
        }
    for (int i = 0; i < superPixels.size(); i++)
        if (sizes[i] > 0)
            superPixels[i].meanColor = superPixels[i].meanColor / sizes[i];

    bool fusionActive = true;
    while (fusionActive) {
        fusionActive = false;
        for (int i = 0; i < superPixels.size(); i++) {
            if (sizes[i] > 0 && sizes[i] < minSize) {
                std::map<int, float> voisinDist;
                for (auto& p : superPixels[i].pixels) {
                    for (auto& d : dirs) {
                        int nx = p.x + d[0], ny = p.y + d[1];
                        if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                            int voisin = labels[ny][nx];
                            if (voisin != i && voisin != -1 && sizes[voisin] > 0) {
                                if (voisinDist.count(voisin) == 0)
                                    voisinDist[voisin] = (superPixels[i].meanColor - superPixels[voisin].meanColor).norm();
                            }
                        }
                    }
                }
                if (!voisinDist.empty()) {
                    int best = -1;
                    float bestDist = std::numeric_limits<float>::max();
                    for (auto& [voisin, dist] : voisinDist) {
                        if (dist < bestDist) {
                            best = voisin;
                            bestDist = dist;
                        }
                    }
                    for (auto& p : superPixels[i].pixels) {
                        labels[p.y][p.x] = best;
                        superPixels[best].pixels.push_back(p);
                        superPixels[best].meanColor += imgIn[p.y * width + p.x];
                        sizes[best]++;
                    }
                    superPixels[i].pixels.clear();
                    sizes[i] = 0;
                    fusionActive = true;
                }
            }
        }
    }
    for (int i = 0; i < superPixels.size(); i++)
        if (sizes[i] > 0) {
            Vec3 colorSum(0, 0, 0);
            for (auto& p : superPixels[i].pixels)
                colorSum += imgIn[p.y * width + p.x];
            superPixels[i].meanColor = colorSum / sizes[i];
        }

    for (int y = 0; y < height; y++)
        for (int x = 0; x < width; x++) {
            int label = labels[y][x];
            if (label >= 0 && sizes[label] > 0) {
                Vec3 c = superPixels[label].meanColor;
                pixels[y * width + x] = (0xFF << 24) | (int(c.r) << 16) | (int(c.g) << 8) | int(c.b);
            } else {
                pixels[y * width + x] = 0xFF000000;
            }
        }

    env->ReleaseIntArrayElements(pixels_, pixels, 0);
    LOGD("Traitement Watershed fini (gauss+sobel) : %d superpixels, minSize=%d", (int)markers.size(), minSize);
}

bool testConvergence(const std::vector<std::vector<int>>& oldCentroids, const std::vector<std::vector<int>>& newCentroids) {
    const int epsilonSq = 1;
    for (size_t i = 0; i < oldCentroids.size(); ++i) {
        int dr = oldCentroids[i][0] - newCentroids[i][0];
        int dg = oldCentroids[i][1] - newCentroids[i][1];
        int db = oldCentroids[i][2] - newCentroids[i][2];
        int distSq = dr * dr + dg * dg + db * db;
        if (distSq > epsilonSq) return false;
    }
    return true;
}


extern "C" JNIEXPORT jintArray  JNICALL
Java_com_example_superpixelapp_worker_CompressionWorker_compression(
        JNIEnv* env, jobject /* this */, jintArray pixels, jint width, jint height, jstring path) {

    jint* tabPixels = env->GetIntArrayElements(pixels, nullptr);
    int taille = width * height;
    LOGD("Appel de CompressionPallette OK");

    const int k = 256;
    std::vector<std::vector<int>> centroids(k, std::vector<int>(3));
    for (int i = 0; i < k; ++i) {

        centroids[i][0] = rand() % 256;
        centroids[i][1] = rand() % 256;
        centroids[i][2] = rand() % 256;
    }

    bool converged = false;
    std::vector<std::vector<std::vector<int>>> clusters(k);
    std::vector<std::vector<int>> newCentroids(k, std::vector<int>(3, 0));
    int iteration=0;

    while (!converged and iteration<10) {
        for (auto& cluster : clusters) {
            cluster.clear();
        }

        for (int i = 0; i < taille; ++i) {
            jint pixel = tabPixels[i];

            int r = (pixel >> 16) & 0xff;
            int g = (pixel >> 8) & 0xff;
            int b = pixel & 0xff;

            int indexMin = 0;
            float distanceMin = std::numeric_limits<float>::max();

            for (int c = 0; c < k; ++c) {
                int dr = centroids[c][0] - r;
                int dg = centroids[c][1] - g;
                int db = centroids[c][2] - b;
                int distance = dr * dr + dg * dg + db * db;


                if (distance < distanceMin) {
                    distanceMin = distance;
                    indexMin = c;
                }
            }

            clusters[indexMin].push_back({r, g, b});
        }

        for (int i = 0; i < k; ++i) {
            newCentroids[i][0] = 0;
            newCentroids[i][1] = 0;
            newCentroids[i][2] = 0;
        }

        for (int i = 0; i < k; ++i) {
            if (!clusters[i].empty()) {
                int sumR = 0, sumG = 0, sumB = 0;
                for (const auto& pixel : clusters[i]) {
                    sumR += pixel[0];
                    sumG += pixel[1];
                    sumB += pixel[2];
                }
                int clusterSize = clusters[i].size();
                newCentroids[i][0] = sumR / clusterSize;
                newCentroids[i][1] = sumG / clusterSize;
                newCentroids[i][2] = sumB / clusterSize;
            } else {
                newCentroids[i] = centroids[i];
            }
        }

        converged = testConvergence(centroids, newCentroids);
        centroids = newCentroids;
        iteration++;
        std::ostringstream oss;
        oss << "Itération : " << iteration;
        LOGD("%s", oss.str().c_str());
    }
    LOGD("Convergence Atteinte");


    std::vector<jint> clusterGrayMap(taille);

    for (int i = 0; i < taille; ++i) {
        jint pixel = tabPixels[i];
        int a = (pixel >> 24) & 0xff;
        int r = (pixel >> 16) & 0xff;
        int g = (pixel >> 8) & 0xff;
        int b = pixel & 0xff;

        int indexMin = 0;
        float distanceMin = std::numeric_limits<float>::max();

        for (int c = 0; c < k; ++c) {
            float distance = std::sqrt(
                    std::pow(centroids[c][0] - r, 2) +
                    std::pow(centroids[c][1] - g, 2) +
                    std::pow(centroids[c][2] - b, 2)
            );
            if (distance < distanceMin) {
                distanceMin = distance;
                indexMin = c;
            }
        }

        int newR = centroids[indexMin][0];
        int newG = centroids[indexMin][1];
        int newB = centroids[indexMin][2];
        tabPixels[i] = (a << 24) | (newR << 16) | (newG << 8) | newB;

        int gray = indexMin;
        clusterGrayMap[i] = (0xFF << 24) | (gray << 16) | (gray << 8) | gray;
    }

    env->ReleaseIntArrayElements(pixels, tabPixels, 0);

    jintArray grayArray = env->NewIntArray(taille);
    env->SetIntArrayRegion(grayArray, 0, taille, clusterGrayMap.data());

    LOGD("Compression Pallette Terminée");

    const char* pathCStr = env->GetStringUTFChars(path, nullptr);
    std::string jsonFilePath(pathCStr);
    env->ReleaseStringUTFChars(path, pathCStr);

    savePaletteToJson(centroids, jsonFilePath);

    return grayArray;

}



Vec3 RGBtoLAB(const Vec3& color) {

    float r = color.r / 255.f;
    float g = color.g / 255.f;
    float b = color.b / 255.f;

    float X = 0.4124f * r + 0.3576f * g + 0.1805f * b;
    float Y = 0.2126f * r + 0.7152f * g + 0.0722f * b;
    float Z = 0.0193f * r + 0.1192f * g + 0.9505f * b;

    auto f = [](float t) { return (t > 0.008856f) ? std::cbrtf(t) : (7.787f * t + 16.f / 116.f); };
    float fx = f(X / 0.95047f);
    float fy = f(Y / 1.00000f);
    float fz = f(Z / 1.08883f);

    float L = 116.f * fy - 16.f;
    float a = 500.f * (fx - fy);
    float b2 = 200.f * (fy - fz);
    return Vec3(L, a, b2);
}


Vec3 LABtoRGB(const Vec3& lab) {

    float y = (lab.r + 16.f) / 116.f;
    float x = lab.g / 500.f + y;
    float z = y - lab.b / 200.f;

    x = 0.95047f * (x * x * x);
    y = 1.00000f * (y * y * y);

    z = 1.08883f * (z * z * z);

    float r = x * 3.2406f + y * -1.5372f + z * -0.4986f;

    float g = x * -0.9689f + y * 1.8758f + z * 0.0415f;

    float b = x * 0.0557f + y * -0.2040f + z * 1.0570f;

    auto clamp = [](float v) { return std::max(0, std::min(255, int(v * 255.f))); };
    return Vec3(clamp(r), clamp(g), clamp(b));
}



extern "C"
JNIEXPORT void JNICALL
Java_com_example_superpixelapp_MainFragment_CreationFragment_traiterImageSLIC(
        JNIEnv* env, jobject thiz,
        jintArray pixels_, jint width, jint height,
        jint nSuperpixels, jfloat compactness)
{
    jint* pixels = env->GetIntArrayElements(pixels_, nullptr);
    if (!pixels) return;

    int S = std::sqrt((width * height) / nSuperpixels);
    int nIter = 10;

    std::vector<Vec3> imgLAB(width * height);
    for (int i = 0; i < width * height; ++i) {
        int pixel = pixels[i];
        int r = (pixel >> 16) & 0xff;
        int g = (pixel >> 8) & 0xff;
        int b = pixel & 0xff;
        imgLAB[i] = RGBtoLAB(Vec3(r, g, b));
    }

    struct Center { Vec2 pos; Vec3 color; };
    std::vector<Center> centers;
    for (int y = S/2; y < height; y += S) {
        for (int x = S/2; x < width; x += S) {
            int idx = y * width + x;
            centers.push_back({Vec2(x, y), imgLAB[idx]});
        }
    }
    int K = centers.size();

    std::vector<int> labels(width * height, -1);
    std::vector<float> distances(width * height, 1e10f);

    for (int iter = 0; iter < nIter; ++iter) {
        std::fill(distances.begin(), distances.end(), 1e10f);
        for (int k = 0; k < K; ++k) {
            Vec2 c = centers[k].pos;
            Vec3 clab = centers[k].color;
            for (int dy = -S; dy <= S; ++dy) {

                int yy = c.y + dy;
                if (yy < 0 || yy >= height) continue;
                for (int dx = -S; dx <= S; ++dx) {

                    int xx = c.x + dx;
                    if (xx < 0 || xx >= width) continue;
                    int idx = yy * width + xx;
                    Vec3 lab = imgLAB[idx];
                    float dc = (lab - clab).norm();
                    float ds = std::sqrt(float(dx * dx + dy * dy));

                    float D = dc + (compactness / S) * ds;
                    if (D < distances[idx]) {
                        distances[idx] = D;
                        labels[idx] = k;
                    }
                }
            }
        }
        std::vector<Vec2> sumXY(K, Vec2(0, 0));
        std::vector<Vec3> sumLAB(K, Vec3(0, 0, 0));
        std::vector<int> count(K, 0);
        for (int i = 0; i < width * height; ++i) {
            int k = labels[i];
            if (k < 0) continue;
            int x = i % width, y = i / width;
            sumXY[k].x += x;

            sumXY[k].y += y;
            sumLAB[k] += imgLAB[i];
            count[k]++;
        }
        for (int k = 0; k < K; ++k) {
            if (count[k] > 0) {
                centers[k].pos = Vec2(sumXY[k].x / count[k], sumXY[k].y / count[k]);
                centers[k].color = sumLAB[k] / float(count[k]);
            }
        }
    }

    for (int i = 0; i < width * height; ++i) {
        int k = labels[i];
        Vec3 lab = centers[k].color;

        Vec3 rgb = LABtoRGB(lab);
        pixels[i] = (0xFF << 24) | (int(rgb.r) << 16) | (int(rgb.g) << 8) | int(rgb.b);
    }

    env->ReleaseIntArrayElements(pixels_, pixels, 0);
}
