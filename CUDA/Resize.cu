#include <stdlib.h>
#include <stdio.h>
#include <math.h>
#include <time.h>
#include "image.cuh" 
#ifdef _WIN32
#include "cuda_runtime.h"
#endif // _WIN32

#define MIN(a,b) (((a)<(b))?(a):(b))
#define MAX(a,b) (((a)>(b))?(a):(b))

/*Notes
* types short instead of int? (width height, pixelEnergy) half of MemoryUsage
* #1: Order?
 */
/*__device__
unsigned int getColorDiff(unsigned char* imgData, int width, int x0, int y0, int x1, int y1) {
            //              Pixel0                                                  Pixel1
    return  abs(imgData[y0 * width * 3 + x0 * 3 + 0] - imgData[y1 * width * 3 + x1 * 3 + 0]) + //R-Value
            abs(imgData[y0 * width * 3 + x0 * 3 + 1] - imgData[y1 * width * 3 + x1 * 3 + 1]) + //G-Value
            abs(imgData[y0 * width * 3 + x0 * 3 + 2] - imgData[y1 * width * 3 + x1 * 3 + 2]);  //B-Value
}*/

/*__device__
unsigned int getPixelEnergy(unsigned char* imgData, int width, int height, int x, int y) {
    unsigned int sum = 0;
    for (int i = -1; i < 2; i++) {
        for (int j = -1; j < 2; j++) {
            //sum += getColorDiff(imgData, width, x, y, (x + i) % width , (y + j) % height);
            sum += abs(imgData[y * width * 3 + x * 3 + 0] - imgData[((y + j) % height) * width * 3 + ((x + i) % width) * 3 + 0]) + //R-Value
                abs(imgData[y * width * 3 + x * 3 + 1] - imgData[((y + j) % height) * width * 3 + ((x + i) % width) * 3 + 1]) + //G-Value
                abs(imgData[y * width * 3 + x * 3 + 2] - imgData[((y + j) % height) * width * 3 + ((x + i) % width) * 3 + 2]);  //B-Value
        }
    }
    return sum;
}*/

__device__
unsigned short* calcPixelEnergies(struct imgRawImage* image) {
    int width = image->width;
    int height = image->height;
    unsigned char* imgData = image->lpData;
    unsigned short* output = (unsigned short*)malloc(sizeof(unsigned short) * width * height);

    // #1
    for (int y = 0; y < height; y++){
        for (int x = 0; x < width; x++) {
            unsigned int sum = 0;
            for (int i = -1; i < 2; i++) {
                for (int j = -1; j < 2; j++) {
                    //sum += getColorDiff(imgData, width, x, y, (x + i) % width , (y + j) % height);
                    sum +=  abs(imgData[y * width * 3 + x * 3 + 0] - imgData[((y + j) % height) * width * 3 + ((x + i) % width) * 3 + 0]) + //R-Value
                            abs(imgData[y * width * 3 + x * 3 + 1] - imgData[((y + j) % height) * width * 3 + ((x + i) % width) * 3 + 1]) + //G-Value
                            abs(imgData[y * width * 3 + x * 3 + 2] - imgData[((y + j) % height) * width * 3 + ((x + i) % width) * 3 + 2]);  //B-Value
                }
            }
            //output[y * width + x] = getPixelEnergy(image->lpData, width, height, x, y);
            output[y * width + x] = sum;
        }
    }
    return output;
}

__device__
unsigned int* calculateMinEnergySums(unsigned short* pixelEnergies, int width, int height) {
    unsigned int* output = (unsigned int*)malloc(sizeof(unsigned int) * width * height);
    for (int y = 1; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            if (x == width - 1) { // rightmost pixel of a row
                output[y * width + x] = pixelEnergies[y * width + height] + MIN(output[(y - 1) * width + x - 1], output[(y - 1) * width + x]);
            }else if (x == 0) { // leftmost pixel of a row
                output[y * width + x] = pixelEnergies[y * width + height] + MIN(output[(y - 1) * width + x], output[(y - 1) * width + x + 1]);
            }else{
                output[y * width + x] = pixelEnergies[y * width + height] + MIN(MIN(output[(y - 1) * width + x - 1], output[(y - 1) * width + x]), output[(y - 1) * width + x + 1]);
            }
        }
    }
    return output;
}

__global__
void increaseWidth(struct imgRawImage* image, int numSeams) {
    int height = image->height;
    unsigned int* newMinEnergySums;
    unsigned char* newData;
    unsigned short* newPixelEnergies;

    unsigned short* pixelEnergies = calcPixelEnergies(image);
    unsigned int* minEnergySums = calculateMinEnergySums(pixelEnergies, image->width, image->height);

    // find seams by looking at the bottom row
    //unsigned int mins[numSeams]; doof
    unsigned int* mins = new unsigned int[numSeams];
    int width = image->width;
    for (int k = 0; k < numSeams; ++k) {
        mins[k] = 0;
        for (int j = 0; j < width; ++j) {
            int skip = 0;
            for (int l = 0; l < k; ++l) {
                if (mins[l] == j) {
                    skip = 1;
                    break;
                }
            }
            if (skip == 0 && minEnergySums[(height - 1) * width + j] < minEnergySums[(height - 1) * width + mins[k]]) {
                printf("m1=%i\n", minEnergySums[(height - 1)*width + j]);
                mins[k] = j;
            }
        }
    }

    for (int i = 0; i < numSeams; ++i) { 
        unsigned int minIdx = mins[i];
        // each iteration increases the width by 1
        int width = image->width;
        unsigned char* oldData = image->lpData;
        printf("iteration %i with width=%i and minIdx=%d\n", i, width, minIdx);
        newMinEnergySums = (unsigned int*) malloc(sizeof(unsigned int) * (width + 1) * height);
        newData = (unsigned char*) malloc(sizeof(unsigned char) * 3 * (width + 1) * height);
        newPixelEnergies = (unsigned short*) malloc(sizeof(unsigned int) * (width + 1) * height);

        // copy the pixels on the left side of the seam
        for (int j = 0; j <= minIdx; ++j) {
            newMinEnergySums[(height - 1) * (width + 1) + j] = minEnergySums[(height - 1) * width + j];
            newData[(height - 1) * (width + 1) * 3 + j * 3] = oldData[(height - 1) * width * 3 + j * 3];
            newData[(height - 1) * (width + 1) * 3 + j * 3 + 1] = oldData[(height - 1) * width * 3 + j * 3 + 1];
            newData[(height - 1) * (width + 1) * 3 + j * 3 + 2] = oldData[(height - 1) * width * 3 + j * 3 + 2];
            newPixelEnergies[(height - 1) * (width + 1) + j] = pixelEnergies[(height - 1) * width + j];
        }
        newMinEnergySums[(height - 1) * (width + 1) + minIdx + 1] = minEnergySums[(height - 1) * width + minIdx];
        newData[(height - 1) * (width + 1) * 3 + (minIdx + 1) * 3] = oldData[(height - 1) * width * 3 + minIdx * 3];
        newData[(height - 1) * (width + 1) * 3 + (minIdx + 1) * 3 + 1] = oldData[(height - 1) * width * 3 + minIdx * 3 + 1];
        newData[(height - 1) * (width + 1) * 3 + (minIdx + 1) * 3 + 2] = oldData[(height - 1) * width * 3 + minIdx * 3 + 2];
        newPixelEnergies[(height - 1) * (width + 1) + minIdx + 1] = pixelEnergies[(height - 1) * width + minIdx];
        // move all pixels right of the seam 1 to the right
        for (int j = minIdx + 1; j < width; ++j) {
            newMinEnergySums[(height - 1) * (width + 1) + j + 1] = minEnergySums[(height - 1) * width + j];
            newData[(height - 1) * (width + 1) * 3 + (j + 1) * 3] = oldData[(height - 1) * width * 3 + j * 3];
            newData[(height - 1) * (width + 1) * 3 + (j + 1) * 3 + 1] = oldData[(height - 1) * width * 3 + j * 3 + 1];
            newData[(height - 1) * (width + 1) * 3 + (j + 1) * 3 + 2] = oldData[(height - 1) * width * 3 + j * 3 + 2];
            newPixelEnergies[(height - 1) * (width + 1) + j + 1] = pixelEnergies[(height - 1) * width + j];
        }
        int x = minIdx;
        for (int y = height - 2; y >= 0; --y) {
            unsigned int min;
            if (x == 0) {
                min = MIN(minEnergySums[y * width + x], minEnergySums[y * width + x + 1]);
            }else if (x == width - 1) {
                min = MIN(minEnergySums[y * width + x - 1], minEnergySums[y * width + x]);
            }else {
                min = MIN(minEnergySums[y * width + x - 1], MIN(minEnergySums[y * width + x], minEnergySums[y * width + x + 1]));
            }

            if (x > 0 && minEnergySums[y*width + (x - 1)] == min) {
                x = x - 1;
            }else if (x <= width - 1 && minEnergySums[y * width + (x + 1)] == min) {
                x = x + 1;
            }

            for (int j = 0; j <= x; ++j) {
                newMinEnergySums[y * (width + 1) + j] = minEnergySums[y * width + j];
                newData[y * (width + 1) * 3 + j * 3] = oldData[y * width * 3 + j * 3];
                newData[y * (width + 1) * 3 + j * 3 + 1] = oldData[y * width * 3 + j * 3 + 1];
                newData[y * (width + 1) * 3 + j * 3 + 2] = oldData[y * width * 3 + j * 3 + 2];
                newPixelEnergies[y * (width + 1) + j] = pixelEnergies[y * width + j];
            }
            newMinEnergySums[y * (width + 1) + x + 1] = minEnergySums[y * width + x];
            newData[y * (width + 1) * 3 + (x + 1) * 3] = oldData[y * width * 3 + x * 3];
            newData[y * (width + 1) * 3 + (x + 1) * 3 + 1] = oldData[y * width * 3 + x * 3 + 1];
            newData[y * (width + 1) * 3 + (x + 1) * 3 + 2] = oldData[y * width * 3 + x * 3 + 2];
            newPixelEnergies[y * (width + 1) + x + 1] = pixelEnergies[y * width + x];
            for (int j = x + 1; j < width; ++j) {
                newMinEnergySums[y * (width + 1) + j + 1] = minEnergySums[y * width + j];
                newData[y * (width + 1) * 3 + (j + 1) * 3] = oldData[y * width * 3 + j * 3];
                newData[y * (width + 1) * 3 + (j + 1) * 3 + 1] = oldData[y * width * 3 + j * 3 + 1];
                newData[y * (width + 1) * 3 + (j + 1) * 3 + 2] = oldData[y * width * 3 + j * 3 + 2];
                newPixelEnergies[y * (width + 1) + j + 1] = pixelEnergies[y * width + j];
            }
        }
        free(image->lpData);
        image->lpData = newData;
        image->width = width + 1;
        free(minEnergySums);
        free(pixelEnergies);
        pixelEnergies = newPixelEnergies;
        minEnergySums = newMinEnergySums;
    }
}

int main(int argc, char* argv[]) {
    if (argc != 4) {
        printf("Usage: %s inputJPEG outputJPEG numSeams\n", argv[0]);
        return 0;
    }
    char* inputFile = argv[1];
    char* outputFile = argv[2];
    int numSeams = atoi(argv[3]);
    struct imgRawImage* d_inputImage, d_outputImage;

    struct imgRawImage* input = loadJpegImageFile(inputFile);

    struct imgRawImage* output;
    int pixelBufferSize = (input->width + numSeams )* input->height * 3;
    unsigned char* imageData = (unsigned char*)malloc(sizeof(unsigned char) * pixelBufferSize);
    output = (struct imgRawImage*)malloc(sizeof(struct imgRawImage));
    output->numComponents = input->numComponents;
    output->width = input->width + numSeams;
    output->height = input->height;
    output->lpData = imageData;

    clock_t start = clock();
    cudaMalloc(&d_inputImage, sizeof(input));
    cudaMalloc(&d_outputImage, sizeof(output));

    dim3 threadsPerBlock = (128, 4); //512 Threads per Block (max 3 Blocks per SM)
    dim3 numBlocks = ((output->width + threadsPerBlock.x - 1) / threadsPerBlock.x, (output->height + threadsPerBlock.y - 1) / threadsPerBlock.y); // at least 15*270 = 4050 blocks
    increaseWidth<<<numBlocks, threadsPerBlock>>>(d_inputImage, numSeams);
    struct imgRawImage* outputImage = cudaMemcpy(output, d_outputImage, sizeof(d_outputImage), cudaMemcpyDeviceToHost);
    cudaFree(&d_inputImage);
    cudaFree(&d_outputImage);
    clock_t end = clock();
    printf("Execution time: %4.2f sec\n", (double)((double)(end - start) / CLOCKS_PER_SEC));
    storeJpegImageFile(output, outputFile);
    return 0;
}
