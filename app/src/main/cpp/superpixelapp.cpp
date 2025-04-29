#include <jni.h>
#include <android/log.h>
#include <vector>

extern "C"
JNIEXPORT void JNICALL
Java_com_example_superpixelapp_MainFragment_CompressionFragment_traiterImageNative(
        JNIEnv *env, jobject thiz, jintArray pixels, jint width, jint height) {

    jint* tabPixels = env->GetIntArrayElements(pixels, nullptr);
    int taille = width * height;
    __android_log_print(ANDROID_LOG_DEBUG, "NATIVE_DEBUG", "Appel de traiterImageNative OK");

    for (int i = 0; i < taille; i++) {
        jint pixel = tabPixels[i];

        // Décomposer la couleur
        int a = (pixel >> 24) & 0xff;
        int r = (pixel >> 16) & 0xff;
        int g = (pixel >> 8) & 0xff;
        int b = pixel & 0xff;

        // Inverser les couleurs
        r = 255 - r;
        g = 255 - g;
        b = 255 - b;

        // Recomposer
        tabPixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
    }

    env->ReleaseIntArrayElements(pixels, tabPixels, 0);
}

using namespace std;

bool testConvergence(vector<vector<int>> centroids, vector<vector<int>> newCentroid){
    for(int i=0; i<centroids.size(); i++){
        if(centroids[i][0]-newCentroid[i][0]>=0.1 && centroids[i][1]-newCentroid[i][1]>=0.1 && centroids[i][2]-newCentroid[i][2]>=0.1){
            return false;
        }
    }
    return true;
}

void kmean(vector<int> ImgIn, vector<int> ImgOUT, vector<vector<int>> centroids, int taille){
    int indexMin;
    for(int i=0; i<taille; i+=3){
        indexMin=0;
        vector<int> pixel = {ImgIn[i],ImgIn[i+1],ImgIn[i+2]};
        float d2norm = numeric_limits<float>::max();

        for (int centroid = 0; centroid < centroids.size(); centroid++) {
            float d1norm = sqrt(
                    pow((centroids[centroid][0]) - (pixel[0]), 2) +
                    pow(static_cast<int>(centroids[centroid][1]) - static_cast<int>(pixel[1]), 2) +
                    pow(static_cast<int>(centroids[centroid][2]) - static_cast<int>(pixel[2]), 2)
            );

            if (d1norm < d2norm) {
                d2norm = d1norm;
                indexMin = centroid;
            }
        }
        ImgOUT[i]=centroids[indexMin][0];
        ImgOUT[i + 1]=centroids[indexMin][1];
        ImgOUT[i + 2]=centroids[indexMin][2];
    }
}

void kmeanConv(vector<int> imgSuperPixel, vector<int>ImgOUT, vector<int> ImgCompresse, int taille){
    vector<vector<vector<int>>> clusters;
    vector<vector<int>> centroids;

    for (int i = 0; i < 256; i++) {
        centroids.push_back({rand() % 256, rand() % 256, rand() % 256});
    }

    // Initialisation des clusters
    clusters.resize(centroids.size());

    bool converged = false;

    while (!converged) {

        for (int i = 0; i < taille; i+=3) {
            vector<int> pixel = {
                    imgSuperPixel[i],
                    imgSuperPixel[i + 1],
                    imgSuperPixel[i + 2]
            };

            int indexMin = 0;
            float distanceMin = numeric_limits<float>::max();

            for (int centroid = 0; centroid < centroids.size(); centroid++) {
                float distance = sqrt(
                        pow(centroids[centroid][0] - pixel[0], 2) +
                        pow(centroids[centroid][1] - pixel[1], 2) +
                        pow(centroids[centroid][2] - pixel[2], 2)
                );

                if (distance < distanceMin) {
                    distanceMin = distance;
                    indexMin = centroid;
                }
            }

            if (indexMin < clusters.size()) {
                clusters[indexMin].push_back(pixel);
            }
        }

        vector<vector<int>> newCentroids;
        for (int i = 0; i < clusters.size(); i++) {
            if (clusters[i].empty()) {
                newCentroids.push_back(centroids[i]);
            } else {
                int sommeRed = 0, sommeGreen = 0, sommeBlue = 0;
                for (const auto &pixel : clusters[i]) {
                    sommeRed += pixel[0];
                    sommeGreen += pixel[1];
                    sommeBlue += pixel[2];
                }

                newCentroids.push_back({
                                               sommeRed / static_cast<int>(clusters[i].size()),
                                               sommeGreen / static_cast<int>(clusters[i].size()),
                                               sommeBlue / static_cast<int>(clusters[i].size())
                                       });
            }
        }

        converged = testConvergence(centroids, newCentroids);
        centroids = newCentroids;
    }

    kmean(imgSuperPixel,ImgOUT,centroids,taille);
    for (int index=0; index<taille; index+=3){
        vector<int> pixel = {ImgOUT[index],ImgOUT[index+1],ImgOUT[index+2]};
        for (int i=0; i<centroids.size(); i++){
            if (pixel[0]==centroids[i][0]&&pixel[1]==centroids[i][1]&&pixel[2]==centroids[i][2]){
                ImgCompresse[index/3]=i;
            }
        }
    }
}

// In MainActivity.java:
//    static {
//       System.loadLibrary("superpixelapp");
//    }
//
// Or, in MainActivity.kt:
//    companion object {
//      init {
//         System.loadLibrary("superpixelapp")
//      }
//    }