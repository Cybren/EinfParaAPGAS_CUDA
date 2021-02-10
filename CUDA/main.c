#include <stdlib.h>
#include <stdio.h>
#include <math.h>
#include <time.h>
#include <string.h>
#include "image.h"

#define MIN(a,b) (((a)<(b))?(a):(b))
#define MAX(a,b) (((a)>(b))?(a):(b))
#define mod(a,b) ((a%b + b) % b)
struct container {
    int value;
    short xPos;
};

int partValue(struct container list[], int left, int right) {
    int pivot = list[right].value;
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


void quicksortValue(struct container* list, int left, int right) {
    if (left < right) {
        unsigned int pivot = partValue(list, left, right);

        quicksortValue(list, left, pivot - 1);
        quicksortValue(list, pivot + 1, right);
    }
}

int partShort(unsigned short list[], int left, int right) {
    unsigned short pivot = list[right];
    int x = (left - 1);
    for (int i = left; i < right; ++i) {
        if (list[i] < pivot) {
            x++;
            unsigned short temp = list[i];
            list[i] = list[x];
            list[x] = temp;
        }
    }
    unsigned short temp = list[x + 1];
    list[x + 1] = list[right];
    list[right] = temp;
    return x + 1;
}


void quicksortShort(unsigned short* list, int left, int right) {
    if (left < right) {
        int pivot = partShort(list, left, right);

        quicksortShort(list, left, pivot - 1);
        quicksortShort(list, pivot + 1, right);
    }
}

struct container minContainer(struct container container1, struct container container2) {
    return container1.value < container2.value ? container1 : container2;
}

//checked
unsigned short *calculatePixelEnergies(struct imgRawImage *image) {
    int height = image->height;
    int width = image->width;
    unsigned short* pixelEnergies = (unsigned short*)malloc(sizeof(int) * height * width);
    int sum;
    int actualY;
    int actualX;
    for (int y = 0; y < height; y++){
        for (int x = 0; x < width; x++) {
            sum = 0;
            for (int offsetX = -1; offsetX < 2; offsetX++){
                for (int offsetY = -1; offsetY < 2; offsetY++) {
                    actualY = mod((y + offsetY), height);
                    actualX = mod((x + offsetX), width);
                    sum += abs(image->lpData[(y * width + x) * 3] - image->lpData[(actualY * width + actualX) * 3])
                        + abs(image->lpData[(y * width + x) * 3 + 1] - image->lpData[(actualY * width + actualX) * 3 + 1])
                        + abs(image->lpData[(y * width + x) * 3 + 2] - image->lpData[(actualY * width + actualX) * 3 + 2]);
                }
            }
            //printf("%u ", sum);
            pixelEnergies[y * width + x] = sum;
        }
        //printf("\n");
    }
    return pixelEnergies;
}

//checked
struct container*calculateMinEnergySums(unsigned short *pixelEnergies, int width, int height) {
    struct container*output = (struct container*) malloc(sizeof(struct container) * height * width);

    for (int x = 0; x < width; x++){
        struct container newContainer;
        newContainer.value = pixelEnergies[x];
        newContainer.xPos = x;
        output[x] = newContainer;
        //printf("%u;%u ", newContainer.xPos, newContainer.value);
    }
    //printf("\n");
    for (int y = 1; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            struct container newContainer;
            if (x == width - 1) { // rightmost pixel of a row
                newContainer.value = pixelEnergies[y * width + x] + MIN(output[(y - 1) * width + x - 1].value, output[(y - 1) * width + x].value);
                //printf("%d + %d oder %d = %d\n", pixelEnergies[y * width + x], output[(y - 1) * width + x - 1].value, output[(y - 1) * width + x].value, newContainer.value);
            }else if (x == 0) { // leftmost pixel of a row
                newContainer.value = pixelEnergies[y * width + x] + MIN(output[(y - 1) * width + x].value, output[(y - 1) * width + x + 1].value);
                //printf("%d + %d oder %d = %d\n", pixelEnergies[y * width + x], output[(y - 1) * width + x].value, output[(y - 1) * width + x + 1].value, newContainer.value);
            }else {
                newContainer.value = pixelEnergies[y * width + x] + MIN(MIN(output[(y - 1) * width + x - 1].value, output[(y - 1) * width + x].value), output[(y - 1) * width + x + 1].value);
                //printf("%d + %d oder %d oder %d = %d\n", pixelEnergies[y * width + x], output[(y - 1) * width + x - 1].value, output[(y - 1) * width + x].value, output[(y - 1) * width + x + 1].value, newContainer.value);
            }
            newContainer.xPos = x;
            output[y * width + x] = newContainer;
            //printf("%u;%u ", newContainer.xPos, newContainer.value);
        }
        //printf("\n");
    }
    return output;
}

// increases the number of columns by cols
struct imgRawImage *increaseWidth(struct imgRawImage *image, int numSeams) {
  printf("start\n");
  int width = image->width;
  int height = image->height;
  printf("EP\n");
  unsigned short *pixelEnergies = calculatePixelEnergies(image);
  printf("MS\n");
  struct container *minEnergySums = calculateMinEnergySums(pixelEnergies, image->width, image->height);
  
  printf("QS1\n");
  //checked
  quicksortValue(minEnergySums + width * (height - 1), 0, width - 1);
  unsigned short* seams = malloc(sizeof(unsigned short) * numSeams * height);
  for (int i = 0; i < numSeams; i++){
      seams[numSeams * (height - 1) + i] = minEnergySums[width * (height - 1) + i].xPos;
  }
  printf("QS2\n");
  //checked
  quicksortShort(seams + numSeams * (height - 1), 0, numSeams-1);
  printf("Seams\n");
  //checked
  for (int i = 0; i < numSeams; i++){
      for (int y = height - 2; y > -1; y--) {
          int prevX = seams[(y + 1) * numSeams + i];
          if (prevX == width - 1) { // rightmost pixel of a row
              seams[y * numSeams + i] = minContainer(minEnergySums[y * width + prevX - 1], minEnergySums[y * width + prevX]).xPos;
          }else if (prevX == 0) { // leftmost pixel of a row
              seams[y * numSeams + i] = minContainer(minEnergySums[y * width + prevX], minEnergySums[y * width + prevX + 1]).xPos;
          }else {
              seams[y * numSeams + i] = minContainer(minContainer(minEnergySums[y * width + prevX - 1], minEnergySums[y * width + prevX]), minEnergySums[y * width + prevX + 1]).xPos;
          }
      }
  }
  printf("preFree");
  free(pixelEnergies);
  free(minEnergySums);
  printf("final\n");
  unsigned char* outputImageData = malloc(sizeof(unsigned char) * height * (width + numSeams) * 3);
  image->width = width + numSeams;
  for (int i = 0; i < height; i++){
      int oldX = -1;
      int seamIndex = 0;
      int row = i * (width + numSeams) * 3;
      for (int x = 0; x < (width + numSeams); x++) {
          if (x > 0 && oldX == seams[i * numSeams + seamIndex] && seamIndex < numSeams) {
              //printf("thread %d at %d (%d) copy seam %d at %d\n", threadNum, x, oldX, threadNum * numSeams + seamIndex, seams[threadNum * numSeams + seamIndex]);
              //kopieren klappt
              outputImageData[row + x * 3] = outputImageData[row + (x - 1) * 3];
              outputImageData[row + x * 3 + 1] = outputImageData[row + (x - 1) * 3 + 1];
              outputImageData[row + x * 3 + 2] = outputImageData[row + (x - 1) * 3 + 2];
              seamIndex++;
          }else {
              oldX++;
              outputImageData[row + x * 3] = image->lpData[row + (oldX - i * numSeams) * 3];
              outputImageData[row + x * 3 + 1] = image->lpData[row + (oldX - i * numSeams) * 3 + 1];
              outputImageData[row + x * 3 + 2] = image->lpData[row + (oldX - i * numSeams) * 3 + 2];
          }
      }
  }
  image->lpData = outputImageData;
  return image;
}

int main(int argc, char *argv[]) {
  if (argc < 4) {
    printf("Usage: %s inputJPEG outputJPEG numSeams\n", argv[0]);
    return 0;
  }
  char *inputFile = argv[1];
  char *outputFile = argv[2];
  int seams = atoi(argv[3]);

  struct imgRawImage *input = loadJpegImageFile(inputFile);
  clock_t start = clock();

  struct imgRawImage *output = increaseWidth(input, seams);

  clock_t end = clock();
  printf("Execution time: %4.2f sec\n", (double)((double)(end-start) / CLOCKS_PER_SEC));
  printf("gonna Store\n");
  storeJpegImageFile(output, outputFile);
  return 0;
}
