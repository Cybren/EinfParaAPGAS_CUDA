#include <stdlib.h>
#include <stdio.h>
#include <math.h>
#include <time.h>
#include <stdbool.h>
#include "image.cuh"
#ifdef _WIN32
#include "cuda_runtime.h"
#endif // _WIN32

#define blockWidth1 32
#define blockHeight1 16

#define blockWidth2 128

#define blocksize3 32

#define blocksize4 64 // 1080/15 = 72 => 64

struct container {
    unsigned int value;
    int xPos;
};

//quicksort for values
int partValue(struct container list[], int left, int right) {
    int pivot = list[right].value;
    int x = (left - 1);
    for (int i = left; i < right; ++i) {
        if (list[i].value <= pivot) {
            x++;
            struct container temp = list[i];
            list[i] = list[x];
            list[x] = temp;
        }
    }
    struct container temp = list[x + 1];
    list[x + 1] = list[right];
    list[right] = temp;
    return x + 1;
}


void quicksortValue(struct container* list, int left, int right) {
    if (left < right) {
        unsigned int pivot = partValue(list, left, right);

        quicksortValue(list, left, pivot - 1);
        quicksortValue(list, pivot + 1, right);
    }
}

//wuicksort for int
int partint(unsigned int list[], int left, int right) {
    unsigned int pivot = list[right];
    int x = (left - 1);
    for (int i = left; i < right; ++i) {
        if (list[i] < pivot) {
            x++;
            unsigned int temp = list[i];
            list[i] = list[x];
            list[x] = temp;
        }
    }
    unsigned int temp = list[x + 1];
    list[x + 1] = list[right];
    list[right] = temp;
    return x + 1;
}


void quicksortint(unsigned int* list, int left, int right) {
    if (left < right) {
        int pivot = partint(list, left, right);

        quicksortint(list, left, pivot - 1);
        quicksortint(list, pivot + 1, right);
    }
}

//helpermethods
__device__
unsigned int MIN(int a, int b) {
    return a < b ? a : b;
}

__device__
int MAX(int a, int b) {
    return a > b ? a : b;
}

__device__
int MOD(int a, int b) {
    return ((a % b )+b) % b;
}

__device__
struct container minContainer(struct container container1, struct container container2) {
    return container1.value < container2.value ? container1 : container2;
}

__global__
void debug(unsigned int* seams, int height, int numSeams) {
    for (int y = 0; y < height; y++){
        for (int x = 0; x < numSeams; x++){
            printf("(%d;%d): %d", x, y, seams[y * numSeams + x]);
        }
        printf("\n");
    }
}

//checked
__global__
void calculatePixelEnergies(unsigned char* inputData, unsigned int* pixelEnergies, int width, int height) {
    //__shared__ unsigned int inputTile[blockHeight1+2][blockWidth1+2];
    int bx = blockIdx.x;
    int by = blockIdx.y;
    int tx = threadIdx.x;
    int ty = threadIdx.y;
    int y = by * blockHeight1 + ty;
    int x = bx * blockWidth1 + tx;
    int sum;
    int actualY;
    int actualX;
    if (y < height && x < width) {
        sum = 0;
        for (int offsetX = -1; offsetX < 2; offsetX++) {
            for (int offsetY = -1; offsetY < 2; offsetY++) {
                actualY = MOD((y + offsetY), height);
                actualX = MOD((x + offsetX), width);
                sum += abs(inputData[(y * width + x) * 3] - inputData[(actualY * width + actualX) * 3])
                    + abs(inputData[(y * width + x) * 3 + 1] - inputData[(actualY * width + actualX) * 3 + 1])
                    + abs(inputData[(y * width + x) * 3 + 2] - inputData[(actualY * width + actualX) * 3 + 2]);
            }
        }
        pixelEnergies[y * width + x] = sum;
    }
}

//checked
__global__
void calculateMinEnergySums(unsigned int* pixelEnergies, struct container* minEnergySums, int width, int row) {
    int bx = blockIdx.x;
    int tx = threadIdx.x;
    int x = bx * blockWidth2 + tx;
    //use "tiling"
    __shared__ unsigned int tiledMinEnergySums[blockWidth2];
    if (x < width) {
        if (row == 0) {
            struct container newContainer;
            newContainer.value = pixelEnergies[x];
            newContainer.xPos = x;
            minEnergySums[x] = newContainer;
        }else {
            tiledMinEnergySums[tx] = minEnergySums[(row - 1) * width + x].value;
            __syncthreads();
            struct container newContainer;
            if (x == 0) { // leftmost pixel of a row
                newContainer.value = pixelEnergies[row * width + x] + MIN(tiledMinEnergySums[tx], (tx + 1 < blockWidth2) ? tiledMinEnergySums[tx + 1] : minEnergySums[(row - 1) * width + x + 1].value);
            }else if (x == width - 1) { // rightmost pixel of a row 
                newContainer.value = pixelEnergies[row * width + x] + MIN((tx - 1 > 0) ? tiledMinEnergySums[tx - 1] : minEnergySums[(row - 1) * width + x - 1].value, tiledMinEnergySums[tx]);
            }else {
                newContainer.value = pixelEnergies[row * width + x] + MIN(MIN((tx - 1 > 0) ? tiledMinEnergySums[tx - 1] : minEnergySums[(row - 1) * width + x - 1].value, tiledMinEnergySums[tx]), (tx + 1 < blockWidth2) ? tiledMinEnergySums[tx + 1] : minEnergySums[(row - 1) * width + x + 1].value);
            }
            newContainer.xPos = x;
            minEnergySums[row * width + x] = newContainer;
        }
    }
}
__global__
void calcSeams(struct container* minEnergySums, unsigned int* seams, int inputWidth, int height, int numSeams) {
    int bx = blockIdx.x;
    int tx = threadIdx.x;
    int threadNum = bx * blocksize3 + tx;
    if (threadNum < numSeams) {
        for (int y = height - 2; y > -1; y--) {
            unsigned int prevX = seams[(y + 1) * numSeams + threadNum];
            if (prevX == inputWidth - 1) { // rightmost pixel of a row
                seams[y * numSeams + threadNum] = minContainer(minEnergySums[y * inputWidth + prevX - 1], minEnergySums[y * inputWidth + prevX]).xPos;
            }else if (prevX == 0) { // leftmost pixel of a row
                seams[y * numSeams + threadNum] = minContainer(minEnergySums[y * inputWidth + prevX], minEnergySums[y * inputWidth + prevX + 1]).xPos;
            }else {
                seams[y * numSeams + threadNum] = minContainer(minContainer(minEnergySums[y * inputWidth + prevX - 1], minEnergySums[y * inputWidth + prevX]), minEnergySums[y * inputWidth + prevX + 1]).xPos;
            }
        }
    }
}

__global__
void increaseWidth(unsigned char *inputData, unsigned char *outputData, struct container* minEnergySums, unsigned int* seams, int inputWidth, int height, int numSeams) {
    int bx = blockIdx.x;
    int tx = threadIdx.x;
    int threadNum = bx * blocksize4 + tx;
    if(threadNum < height){
        //int to track where we are in the old picture
        int oldX = -1;
        int seamIndex = 0;
        int outputRow = threadNum * (inputWidth + numSeams) * 3;
        int inputRow = threadNum * inputWidth * 3;
        for (int x = 0; x < (inputWidth + numSeams); x++) {
            /*bool condition = (x > 0 && oldX == seams[threadNum * numSeams + seamIndex] && seamIndex < numSeams);
            oldX = condition ? oldX: oldX + 1;
            outputData[row + x * 3] = condition ? outputData[row + (x - 1) * 3] : inputData[inputRow + oldX * 3];
            outputData[row + x * 3 + 1] = condition ? outputData[row + (x - 1) * 3 + 1] : inputData[inputRow + oldX * 3 + 1];
            outputData[row + x * 3 + 2] = condition ? outputData[row + (x - 1) * 3 + 2] : inputData[inputRow + oldX * 3 + 2];
            seamIndex = condition ? seamIndex + 1 : seamIndex;*/
            //copy last pixel if oldX is at a point where a seam is 
            if (x > 0 && oldX == seams[threadNum * numSeams + seamIndex] && seamIndex < numSeams) {
                outputData[outputRow + x * 3] = outputData[outputRow + (x - 1) * 3];
                outputData[outputRow + x * 3 + 1] = outputData[outputRow + (x - 1) * 3 + 1];
                outputData[outputRow + x * 3 + 2] = outputData[outputRow + (x - 1) * 3 + 2];
                seamIndex++;
            }else {
            //else just copy the pixel of the old picture
                oldX++;
                outputData[outputRow + x * 3] = inputData[inputRow + oldX * 3];
                outputData[outputRow + x * 3 + 1] = inputData[inputRow + oldX * 3 + 1];
                outputData[outputRow + x * 3 + 2] = inputData[inputRow + oldX * 3 + 2];
            }
        }
    }
}

int main(int argc, char* argv[]) {
    if (argc < 4) {
        printf("Usage: %s inputJPEG outputJPEG numSeams\n", argv[0]);
        return 0;
    }
    char* inputFile = argv[1];
    char* outputFile = argv[2];
    int numSeams = atoi(argv[3]);

    struct imgRawImage* input = loadJpegImageFile(inputFile);
    clock_t start = clock();
    //host
    int width = input->width;
    int height = input->height;


    size_t inputDataSize_t = sizeof(unsigned char) * width * height * 3;
    size_t outputDataSize_t = sizeof(unsigned char) * (width + numSeams)* height * 3;
    size_t pixelEnergiesSize_t = sizeof(unsigned int) * width * height;
    size_t minEnergySumsSize_t = sizeof(struct container) * height * width;
    size_t seamsSize_t = sizeof(unsigned int) * numSeams * height;
    size_t seamStartSize_t = sizeof(unsigned int) * numSeams;
    size_t lastMinEnergySumsSize_t = sizeof(struct container) * width;

    unsigned int* seamsStart = (unsigned int*)malloc(seamStartSize_t);
    struct container* lastMinEnergySums = (struct container*)malloc(lastMinEnergySumsSize_t);
    unsigned char* outputData = (unsigned char*)malloc(outputDataSize_t);
    cudaError cudaStatus;
    //device
    unsigned char* d_inputData;
    unsigned char* d_outputData;
    unsigned int* d_pixelEnergies;
    struct container* d_minEnergySums;
    unsigned int* d_seams;

    //allocate Devicememory
    cudaStatus = cudaMalloc(&d_inputData, inputDataSize_t);
    if (cudaStatus != cudaSuccess) { fprintf(stderr, "malloc d_inputData failed! ErrorCode %d: %s\n", cudaStatus, cudaGetErrorString(cudaStatus)); }
    cudaMemset(d_inputData, 0, inputDataSize_t);
    cudaStatus = cudaMalloc(&d_outputData, outputDataSize_t);
    if (cudaStatus != cudaSuccess) { fprintf(stderr, "malloc d_inputImage failed! ErrorCode %d: %s\n", cudaStatus, cudaGetErrorString(cudaStatus)); }
    cudaMemset(d_outputData, 0, outputDataSize_t);
    cudaStatus = cudaMalloc(&d_pixelEnergies, pixelEnergiesSize_t);
    if (cudaStatus != cudaSuccess) { fprintf(stderr, "malloc d_pixelEnergies failed! ErrorCode %d: %s\n", cudaStatus, cudaGetErrorString(cudaStatus)); }
    cudaMemset(d_pixelEnergies, 0, pixelEnergiesSize_t);
    cudaStatus = cudaMalloc(&d_minEnergySums, minEnergySumsSize_t);
    if (cudaStatus != cudaSuccess) { fprintf(stderr, "malloc d_minEnergySums failed! ErrorCode %d: %s\n", cudaStatus, cudaGetErrorString(cudaStatus)); }
    cudaMemset(d_minEnergySums, 0, minEnergySumsSize_t);
    cudaStatus = cudaMalloc(&d_seams, seamsSize_t);
    if (cudaStatus != cudaSuccess) { fprintf(stderr, "malloc d_minEnergySums failed! ErrorCode %d: %s\n", cudaStatus, cudaGetErrorString(cudaStatus)); }
    cudaMemset(d_seams, 0, seamsSize_t);

    //start kernel1 calculatePixelEnergies
    cudaStatus = cudaMemcpy(d_inputData, input->lpData, inputDataSize_t, cudaMemcpyHostToDevice);
    if (cudaStatus != cudaSuccess) { fprintf(stderr, "Memory Copy input->lpData -> d_inputData failed! ErrorCode %d: %s\n", cudaStatus, cudaGetErrorString(cudaStatus)); }
    dim3 threadsPerBlock1(blockWidth1, blockHeight1);
    dim3 numBlocks1(ceil(width / (double)threadsPerBlock1.x), ceil(height / (double)threadsPerBlock1.y));

    calculatePixelEnergies<<<numBlocks1, threadsPerBlock1>>>(d_inputData, d_pixelEnergies, width, height);

    cudaStatus = cudaGetLastError();
    if (cudaStatus != cudaSuccess) { fprintf(stderr, "calculatePixelEnergies launch failed: %s\n", cudaGetErrorString(cudaStatus)); }
    cudaStatus = cudaDeviceSynchronize();
    if (cudaStatus != cudaSuccess) { fprintf(stderr, "cudaDeviceSynchronize after launch calculatePixelEnergies failed: %s\n", cudaGetErrorString(cudaStatus)); }

    //start kernel2 calculateMinEnergySums
    dim3 threadsPerBlock2(blockWidth2);
    dim3 numBlocks2(ceil(width / (double)threadsPerBlock2.x));

    for (int i = 0; i < height; i++){
        calculateMinEnergySums << <numBlocks2, threadsPerBlock2 >> > (d_pixelEnergies, d_minEnergySums, width, i);
        cudaStatus = cudaDeviceSynchronize();
        if (cudaStatus != cudaSuccess) { fprintf(stderr, "cudaDeviceSynchronize after launch calculateMinEnergySums failed: %s\n", cudaGetErrorString(cudaStatus)); }
    }
    cudaStatus = cudaGetLastError();
    if (cudaStatus != cudaSuccess) { fprintf(stderr, "calculateMinEnergySums launch failed: %s\n", cudaGetErrorString(cudaStatus)); }
    cudaStatus = cudaDeviceSynchronize();
    if (cudaStatus != cudaSuccess) { fprintf(stderr, "cudaDeviceSynchronize after launch calculateMinEnergySums failed: %s\n", cudaGetErrorString(cudaStatus)); }

    //calculate Seams schauen wegen k>width
    cudaStatus = cudaMemcpy(lastMinEnergySums, d_minEnergySums + width * (height - 1), lastMinEnergySumsSize_t, cudaMemcpyDeviceToHost);
    if (cudaStatus != cudaSuccess) { fprintf(stderr, "Memory Copy d_minEnergySums -> lastMinEnergySums failed! ErrorCode %d: %s\n", cudaStatus, cudaGetErrorString(cudaStatus)); }

    //sort by value
    quicksortValue(lastMinEnergySums, 0, width - 1);
    /*for (int i = 0; i < numSeams; i++) {
        seamsStart[i] = lastMinEnergySums[i].xPos;
    }*/
    int seamIndex = 0;
    int minSumIndex = 0;
    while (seamIndex < numSeams) {
        seamsStart[seamIndex] = lastMinEnergySums[minSumIndex].xPos;
        seamIndex++;
        minSumIndex = (minSumIndex + 1) % width;
    }

    //sort by coordinate
    quicksortint(seamsStart, 0, numSeams - 1);
    cudaStatus = cudaMemcpy(d_seams + numSeams * (height - 1), seamsStart, seamStartSize_t, cudaMemcpyHostToDevice);
    if (cudaStatus != cudaSuccess) { fprintf(stderr, "Memory Copy seamsStart -> d_seams failed! ErrorCode %d: %s\n", cudaStatus, cudaGetErrorString(cudaStatus)); }

    //start kernel3 calcSeams
    dim3 threadsPerBlock3(blocksize3);
    dim3 numBlocks3(ceil(numSeams/ (double)blocksize3));
    printf("\n%d %d\n", threadsPerBlock3.x, numBlocks3.x);
    calcSeams<<<numBlocks3, threadsPerBlock3 >>>(d_minEnergySums, d_seams, width, height, numSeams);

    cudaStatus = cudaGetLastError();
    if (cudaStatus != cudaSuccess) { fprintf(stderr, "calcSeams launch failed: %s\n", cudaGetErrorString(cudaStatus)); }
    cudaStatus = cudaDeviceSynchronize();
    if (cudaStatus != cudaSuccess) { fprintf(stderr, "cudaDeviceSynchronize after launch calcSeams failed: %s\n", cudaGetErrorString(cudaStatus)); }

    //start kernel4 increaseWidth
    dim3 threadsPerBlock4(blocksize4);
    dim3 numBlocks4(ceil(height / (double)blocksize4));
    increaseWidth<<<numBlocks4, threadsPerBlock4>>>(d_inputData, d_outputData, d_minEnergySums, d_seams, width, height, numSeams);
    cudaStatus = cudaGetLastError();
    if (cudaStatus != cudaSuccess) { fprintf(stderr, "increaseWidth launch failed: %s\n", cudaGetErrorString(cudaStatus)); }
    cudaStatus = cudaDeviceSynchronize();
    if (cudaStatus != cudaSuccess) { fprintf(stderr, "cudaDeviceSynchronize after launch increaseWidth failed: %s\n", cudaGetErrorString(cudaStatus)); }

    //copy outputData and create image
    cudaStatus = cudaMemcpy(outputData, d_outputData, outputDataSize_t, cudaMemcpyDeviceToHost);
    if (cudaStatus != cudaSuccess) { fprintf(stderr, "Memory Copy d_outputData -> outputData failed! ErrorCode %d: %s\n", cudaStatus, cudaGetErrorString(cudaStatus)); }
    input->width = width + numSeams;
    input->lpData = outputData;

    //free Memory
    cudaFree(&d_inputData);
    cudaFree(&d_outputData);
    cudaFree(&d_pixelEnergies);
    cudaFree(&d_minEnergySums);
    cudaFree(&d_seams);

    clock_t end = clock();
    printf("Execution time: %4.2f sec\n", (double)((double)(end - start) / CLOCKS_PER_SEC));
    storeJpegImageFile(input, outputFile);
    free(seamsStart);
    free(lastMinEnergySums);
    free(outputData);
    return 0;
}
