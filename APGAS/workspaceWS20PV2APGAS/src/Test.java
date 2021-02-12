public class Test {

    public static void main(String[] args) {
        int[][] testArray = {{1,2,3},{4,5,6}};
        System.out.println(testArray);
        System.out.println(testArray[0]);
        int[] inputArray = {4,5,6};
        testArray[0] = inputArray;
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 3; j++) {
                System.out.println(testArray[i][j] + " ");
            }
            System.out.println();
        }
    }
}
