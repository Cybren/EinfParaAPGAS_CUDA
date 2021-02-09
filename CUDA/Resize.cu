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
#define blockWidth1 32
#define blockHeight1 16

#define blockWidth2 128
#define blockHeight2 4

#define blocksize3 64 // 1080/15 = 72 => 64

struct container{
    int value;
    short xPos;
};

/*Notes
* types short instead of int? (width height, pixelEnergy) half of MemoryUsage
* #1: Order?
 */


int part(struct container list[], int left, int right) {
    unsigned int pivot = list[right].value;
    int x = (left - 1);
    for (int i = left; i < right; ++i) {
        if (list[i].value < pivot) {
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


void quicksort(struct container* list, int left, int right) {
    if (left < right) {
        unsigned int pivot = part(list, left, right);

        quicksort(list, left, pivot - 1);
        quicksort(list, pivot + 1, right);
    }
}

__device__
struct container min(struct container container1, struct container container2) {
    return container1.value < container2.value ? container1 : container2;
}

__global__
void calcPixelEnergies(unsigned char* imageData, unsigned short* energyBuffer, int width, int height) {
    short bx = blockIdx.x;
    short by = blockIdx.y;
    short tx = threadIdx.x;
    short ty = threadIdx.y;
    short y = by * blockHeight1 + ty;
    short x = bx * blockWidth1 + tx;
    if (x == 0 && y == 0) {
        printf("calcPixelEnergies\n");
    }
    if (y < height && x < width) {
        unsigned int sum = 0;
        for (int i = -1; i < 2; i++) {
            for (int j = -1; j < 2; j++) {
                sum +=  abs(imageData[y * width * 3 + x * 3 + 0] - imageData[((y + j) % height) * width * 3 + ((x + i) % width) * 3 + 0]) + //R-Value
                        abs(imageData[y * width * 3 + x * 3 + 1] - imageData[((y + j) % height) * width * 3 + ((x + i) % width) * 3 + 1]) + //G-Value
                        abs(imageData[y * width * 3 + x * 3 + 2] - imageData[((y + j) % height) * width * 3 + ((x + i) % width) * 3 + 2]);  //B-Value
            }
        }
        energyBuffer[y * width + x] = sum;
    }
}

__global__
void calculateMinEnergySums(unsigned short* energyBuffer, struct container* sumBuffer, int width, int height) {
    short bx = blockIdx.x;
    short by = blockIdx.y;
    short tx = threadIdx.x;
    short ty = threadIdx.y;
    short y = by * blockHeight2 + ty;
    short x = bx * blockWidth2 + tx;
    struct container newContainer;
    if (x == 0 && y == 0) {
        printf("calculateMinEnergySums\n");
    }
    if (y < height && x < width) {
        if (x == width - 1) { // rightmost pixel of a row
            newContainer.value = energyBuffer[y * width + x] + MIN(energyBuffer[(y - 1) * width + x - 1], energyBuffer[(y - 1) * width + x]);
            newContainer.xPos = x;
        }else if (x == 0) { // leftmost pixel of a row
            newContainer.value = energyBuffer[y * width + x] + MIN(energyBuffer[(y - 1) * width + x], energyBuffer[(y - 1) * width + x + 1]);
            newContainer.xPos = x;
        }else {
            newContainer.value = energyBuffer[y * width + x] + MIN(MIN(energyBuffer[(y - 1) * width + x - 1], energyBuffer[(y - 1) * width + x]), energyBuffer[(y - 1) * width + x + 1]);
            newContainer.xPos = x;
        }
        sumBuffer[y * width + x] = newContainer;
    }
}

__global__
void increaseWidth(unsigned char* imageData, unsigned char* outputImageData, struct container* sumBuffer, unsigned short* seams, int numSeams, int inputWidth, int height) {
    int bx = blockIdx.x;
    int tx = threadIdx.x;
    int threadNum = bx * blocksize3 + tx;
    int outputWidth = inputWidth + numSeams;
    if (threadNum == 0) {
        printf("increaseWidth numSeams: %d width: %d height: %d\n", numSeams, inputWidth, height);
    }
    //find seams
    if (threadNum < numSeams) {//sumBuffer sizeof(container) * input->width * input->height; seams sizeof(unsigned short) * input->height * numSeams
        seams[(height - 1) * numSeams + threadNum] = sumBuffer[inputWidth* (height-1) + threadNum].xPos;
        for (int y = height-2; y > -1; y--){
            int prevX = seams[threadNum * height + y + 1];
            if (prevX == inputWidth - 1) { // rightmost pixel of a row
                seams[y * numSeams + threadNum] = min(sumBuffer[y * inputWidth + prevX - 1], sumBuffer[y * inputWidth + prevX]).xPos;
            }else if (prevX == 0) { // leftmost pixel of a row
                seams[y * numSeams + threadNum] = min(sumBuffer[y * inputWidth + prevX], sumBuffer[y * inputWidth + prevX + 1]).xPos;
            }else {
                seams[y * numSeams + threadNum] = min(min(sumBuffer[y * inputWidth + prevX - 1], sumBuffer[y * inputWidth + prevX]), sumBuffer[y * inputWidth + prevX + 1]).xPos;
            }
        }
    }
    if (threadNum == 0) {
        printf("increaseWidth after seams\n");
    }
    __syncthreads();
    //create final Image
    if (threadNum < height) {//Mehr Threads möglich (*3) und Jeder Thread eine Farbe
        int oldX = 0;
        int seamIndex = 0;
        int row = threadNum * outputWidth * 3;
        for (int x = 0; x < outputWidth; x++) {// illegal Memory Access
            /*if (oldX == seams[threadNum * width + seamIndex] && x > 0) {
                outputImageData[(threadNum * width) + x * 3]     = outputImageData[(threadNum * width) + (x - 1) * 3];
                outputImageData[(threadNum * width) + x * 3 + 1] = outputImageData[(threadNum * width) + (x - 1) * 3 + 1];
                outputImageData[(threadNum * width) + x * 3 + 2] = outputImageData[(threadNum * width) + (x - 1) * 3 + 2];
                seamIndex++;
            }else{*/
                outputImageData[row + x * 3]     = imageData[row + oldX * 3];
                outputImageData[row + x * 3 + 1] = imageData[row + oldX * 3 + 1];
                outputImageData[row + x * 3 + 2] = imageData[row + oldX * 3 + 2];
                oldX++;
            //}
        }
    }
    if (threadNum == 0) {
        printf("increaseWidth at end\n");
    }
}

int main(int argc, char* argv[]) {
    printf("start");
    if (argc != 4) {
        printf("Usage: %s inputJPEG outputJPEG numSeams\n", argv[0]);
        return 0;
    }
    char* inputFile = argv[1];
    char* outputFile = argv[2];
    int numSeams = atoi(argv[3]);
    //load image
    struct imgRawImage* input = loadJpegImageFile(inputFile);
    clock_t start = clock();
    //TO-DO use multiple GPUS
    //catch cuda errors
    cudaError_t cudaStatus = cudaSetDevice(0);
    if (cudaStatus != cudaSuccess) { fprintf(stderr, "cudaSetDevice failed! ErrorCode %d: %s\n", cudaStatus, cudaGetErrorString(cudaStatus)); }

    int outputPixelBufferSize = (input->width + numSeams) * input->height;
    int inputPixelBufferSize = input->width * input->height;
    int sumBufferSize = sizeof(container) * input->width * input->height;
    unsigned char* d_inputImageData;
    unsigned char* d_outputImageData;
    unsigned short* d_energyBuffer;
    struct container* d_sumBuffer;
    struct container* sumBuffer;
    unsigned short* d_seams;

    //create outputimage struct
    struct imgRawImage* output;
    unsigned char* outputImageData = (unsigned char*)malloc(sizeof(unsigned char) * outputPixelBufferSize * 3);
    sumBuffer = (struct container*)malloc(sumBufferSize);
    output = (struct imgRawImage*)malloc(sizeof(struct imgRawImage));
    output->numComponents = input->numComponents;
    output->width = input->width + numSeams;
    output->height = input->height;

    //allocate nessessary memory on GPU
    cudaStatus = cudaMalloc(&d_inputImageData, sizeof(unsigned char) * inputPixelBufferSize * 3);
    if (cudaStatus != cudaSuccess) { fprintf(stderr, "malloc d_inputImage failed! ErrorCode %d: %s\n", cudaStatus, cudaGetErrorString(cudaStatus)); }
    cudaStatus = cudaMalloc(&d_outputImageData, sizeof(unsigned char) * outputPixelBufferSize * 3);
    if (cudaStatus != cudaSuccess) { fprintf(stderr, "malloc d_imageData failed! ErrorCode %d: %s\n", cudaStatus, cudaGetErrorString(cudaStatus)); }
    cudaStatus = cudaMalloc(&d_energyBuffer, sizeof(unsigned short) * outputPixelBufferSize);
    if (cudaStatus != cudaSuccess) { fprintf(stderr, "malloc d_energyBuffer failed! ErrorCode %d: %s\n", cudaStatus, cudaGetErrorString(cudaStatus)); }
    cudaStatus = cudaMalloc(&d_sumBuffer, sumBufferSize);
    if (cudaStatus != cudaSuccess) { fprintf(stderr, "malloc d_sumBuffer failed! ErrorCode %d: %s\n", cudaStatus, cudaGetErrorString(cudaStatus)); }
    cudaStatus = cudaMalloc(&d_seams, sizeof(unsigned short) * input->height * numSeams);
    if (cudaStatus != cudaSuccess) { fprintf(stderr, "malloc d_seams failed! ErrorCode %d: %s\n", cudaStatus, cudaGetErrorString(cudaStatus)); }
    
    //start Kernel 1 to calculate all Energies
    cudaStatus = cudaMemcpy(d_inputImageData, input->lpData, sizeof(unsigned char) * inputPixelBufferSize, cudaMemcpyHostToDevice);
    if (cudaStatus != cudaSuccess) { fprintf(stderr, "Memory Copy input->lpData -> d_inputImageData failed! ErrorCode %d: %s\n", cudaStatus, cudaGetErrorString(cudaStatus)); }
    dim3 threadsPerBlock1(blockWidth1, blockHeight1);
    dim3 numBlocks1(ceil(input->width / threadsPerBlock1.x), ceil(input->height / threadsPerBlock1.y));
    calcPixelEnergies <<<numBlocks1, threadsPerBlock1>>>(d_inputImageData, d_energyBuffer, input->width, input->height);
    cudaStatus = cudaGetLastError();
    if (cudaStatus != cudaSuccess) { fprintf(stderr, "calcPixelEnergies launch failed: %s\n", cudaGetErrorString(cudaStatus)); }
    cudaStatus = cudaDeviceSynchronize();
    if (cudaStatus != cudaSuccess) { fprintf(stderr, "cudaDeviceSynchronize after launch calcPixelEnergies failed: %s\n", cudaGetErrorString(cudaStatus)); }

    //start kernel2 to calculate the lowest energy-sums
    dim3 threadsPerBlock2(blockWidth2, blockHeight2);
    dim3 numBlocks2(ceil(input->width / threadsPerBlock2.x ), ceil(input->height / threadsPerBlock2.y));
    calculateMinEnergySums <<<numBlocks2, threadsPerBlock2>>>(d_energyBuffer, d_sumBuffer, input->width, input->height);
    cudaStatus = cudaGetLastError();
    if (cudaStatus != cudaSuccess) { fprintf(stderr, "calculateMinEnergySums launch failed: %s\n", cudaGetErrorString(cudaStatus)); }
    //cudaFree(d_energyBuffer);
    cudaStatus = cudaDeviceSynchronize();
    if (cudaStatus != cudaSuccess) { fprintf(stderr, "cudaDeviceSynchronize after launch calculateMinEnergySums failed: %s\n", cudaGetErrorString(cudaStatus)); }

    //copy geht sehr viel kleiner, nur zu faul
    //calculate lowest energy-sums in last row on cpu
    printf("wanna calculate seams\n");
    /*cudaStatus = cudaMemcpy(sumBuffer, d_sumBuffer, sumBufferSize, cudaMemcpyDeviceToHost);
    if (cudaStatus != cudaSuccess) { fprintf(stderr, "Memory Copy d_sumBuffer -> sumBuffer failed! ErrorCode %d: %s\n", cudaStatus, cudaGetErrorString(cudaStatus)); }
    quicksort(sumBuffer + (input->height - 1) * input->width * sizeof(container), 0, input->width);
    cudaStatus = cudaMemcpy(d_sumBuffer, sumBuffer, sumBufferSize, cudaMemcpyHostToDevice);
    if (cudaStatus != cudaSuccess) { fprintf(stderr, "Memory Copy sumBuffer -> d_sumBuffer failed! ErrorCode %d: %s\n", cudaStatus, cudaGetErrorString(cudaStatus)); }
    */
    //start final kernel to create outputImage
    dim3 threadsPerBlock3(blocksize3);
    dim3 numBlocks3(ceil(output->height / blocksize3));
    increaseWidth <<<numBlocks3, threadsPerBlock3 >>>(d_inputImageData, d_outputImageData, d_sumBuffer, d_seams, numSeams, input->width, input->height);
    cudaStatus = cudaGetLastError();
    if (cudaStatus != cudaSuccess) { fprintf(stderr, "increaseWidth launch failed: %s\n", cudaGetErrorString(cudaStatus)); }
    cudaStatus = cudaDeviceSynchronize();
    if (cudaStatus != cudaSuccess) { fprintf(stderr, "cudaDeviceSynchronize after launch increaseWidth failed: %s\n", cudaGetErrorString(cudaStatus)); }
    printf("after Kernel3\n");
    //copy outputData to host
    cudaStatus = cudaMemcpy(outputImageData, d_outputImageData, sizeof(unsigned char) * outputPixelBufferSize * 3, cudaMemcpyDeviceToHost);
    if (cudaStatus != cudaSuccess) { fprintf(stderr, "Memory Copy d_imageData -> outputImageData failed! ErrorCode %d: %s\n", cudaStatus, cudaGetErrorString(cudaStatus)); }
    output->lpData = outputImageData;
    //free all allocated Memory
    cudaFree(&d_inputImageData);
    cudaFree(&d_outputImageData);
    cudaFree(&d_sumBuffer);
    cudaFree(&d_seams);
    cudaFree(&d_energyBuffer);
    clock_t end = clock();
    printf("Execution time: %4.2f sec\n", (double)((double)(end - start) / CLOCKS_PER_SEC));
    storeJpegImageFile(output, outputFile);
    return 0;
}
