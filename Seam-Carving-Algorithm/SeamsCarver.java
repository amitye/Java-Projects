package edu.cg;

import java.awt.*;
import java.awt.image.BufferedImage;

public class SeamsCarver extends ImageProcessor {

    // MARK: An inner interface for functional programming.
    @FunctionalInterface
    interface ResizeOperation {
        BufferedImage resize();
    }

    // MARK: Fields
    private int numOfSeams;
    private ResizeOperation resizeOp;
    boolean[][] imageMask;
    int[][] I = new int[inHeight][inWidth]; // this is the mapping matrix

    public SeamsCarver(Logger logger, BufferedImage workingImage, int outWidth, RGBWeights rgbWeights,
                       boolean[][] imageMask) {
        super((s) -> logger.log("Seam carving: " + s), workingImage, rgbWeights, outWidth, workingImage.getHeight());

        numOfSeams = Math.abs(outWidth - inWidth);
        this.imageMask = imageMask;
        if (inWidth < 2 | inHeight < 2)
            throw new RuntimeException("Can not apply seam carving: workingImage is too small");

        if (numOfSeams > inWidth / 2)
            throw new RuntimeException("Can not apply seam carving: too many seams...");

        // Setting resizeOp with the appropriate method reference
        if (outWidth > inWidth)
            resizeOp = this::increaseImageWidth;
        else if (outWidth < inWidth)
            resizeOp = this::reduceImageWidth;
        else
            resizeOp = this::duplicateWorkingImage;

        // initialized the mapping matrix
        forEach((y, x) -> {
            I[y][x] = x;
        });

        // generate and move all seams
        for (int currentSeamsRemoved = 0; currentSeamsRemoved < numOfSeams; currentSeamsRemoved++) {

            // declare the gradient magnitude and cost matrices
            long[][] E = new long[inHeight][inWidth - currentSeamsRemoved];
            long[][] M = new long[inHeight][inWidth - currentSeamsRemoved];

            calcMatrixE(imageMask, currentSeamsRemoved, E);
            calcMatrixM(currentSeamsRemoved, E, M);
            int[] indicesOfCurrentSeam = findCurrentSeam(currentSeamsRemoved, E, M);
            moveSeamToRightSideOfMappingMatrix(indicesOfCurrentSeam);
        }

        this.logger.log("preliminary calculations were ended.");
    }

    private void moveSeamToRightSideOfMappingMatrix(int[] indicesOfCurrentSeam) {
        // move the current seam to the right side of the mapping matrix
        // while shifting left all the required cells to fill in the gap
        for (int y = 0; y < inHeight; y++) {
            int tmpMappingValue = I[y][indicesOfCurrentSeam[y]];
            for (int x = indicesOfCurrentSeam[y]; x < inWidth - 1; x++) {
                I[y][x] = I[y][x + 1];
            }
            I[y][inWidth - 1] = tmpMappingValue;
        }
    }

    private int[] findCurrentSeam(int currentSeamsRemoved, long[][] E, long[][] M) {
        int[] xIndicesOfCurrentSeam = new int[inHeight];

        // find the beginning of seam in the last row and store it
        int xIndexOfMinimalValueInLastRow = 0;
        long xMinimalValueInLastRow = M[inHeight - 1][0];

        for (int x = 1; x < inWidth - currentSeamsRemoved; x++) {
            if (M[inHeight - 1][x] < xMinimalValueInLastRow) {
                xIndexOfMinimalValueInLastRow = x;
                xMinimalValueInLastRow = M[inHeight - 1][x];
            }
        }
        xIndicesOfCurrentSeam[inHeight - 1] = xIndexOfMinimalValueInLastRow;

        // for each index of the current seam
        // backtrack to find the previous index of the seam, store it and remember the value for next iteration's calculation
        long valueFromSeamsLastIteration = xMinimalValueInLastRow;
        for (int y = inHeight - 2; y >= 0; y--) {
            long currentRowsPixelValue = -1;

            // check if path is from straight above
            if (valueFromSeamsLastIteration == E[y + 1][xIndicesOfCurrentSeam[y+1]] + M[y][xIndicesOfCurrentSeam[y+1]] + costCU(y + 1, xIndicesOfCurrentSeam[y+1], currentSeamsRemoved)) {
                // backtrack straight up
                xIndicesOfCurrentSeam[y] = xIndicesOfCurrentSeam[y + 1];
                currentRowsPixelValue = M[y][xIndicesOfCurrentSeam[y + 1]];
            } else {
                // check if previous pixel was near an edge of the image and backtrack accordingly
                if (xIndicesOfCurrentSeam[y+1] == 0) {
                    // backtrack to the right
                    xIndicesOfCurrentSeam[y] = xIndicesOfCurrentSeam[y+1] + 1;
                    currentRowsPixelValue = M[y][xIndicesOfCurrentSeam[y+1] + 1];
                } else if (xIndicesOfCurrentSeam[y+1] == inWidth - currentSeamsRemoved - 1) {
                    // backtrack to the left
                    xIndicesOfCurrentSeam[y] = xIndicesOfCurrentSeam[y+1] - 1;
                    currentRowsPixelValue = M[y][xIndicesOfCurrentSeam[y+1] - 1];
                } else {
                    // check if path is from the left or from the right and backtrack accordingly
                    if (valueFromSeamsLastIteration == E[y + 1][xIndicesOfCurrentSeam[y+1]] + M[y][xIndicesOfCurrentSeam[y+1] - 1] + costCL(y + 1, xIndicesOfCurrentSeam[y+1], currentSeamsRemoved)) {
                        xIndicesOfCurrentSeam[y] = xIndicesOfCurrentSeam[y+1] - 1;
                        currentRowsPixelValue = M[y][xIndicesOfCurrentSeam[y+1] - 1];
                    } else {
                        xIndicesOfCurrentSeam[y] = xIndicesOfCurrentSeam[y+1] + 1;
                        currentRowsPixelValue = M[y][xIndicesOfCurrentSeam[y+1] + 1];
                    }
                }
            }

            // update for next iteration
            valueFromSeamsLastIteration = currentRowsPixelValue;
        }

        return xIndicesOfCurrentSeam;
    }

    private void calcMatrixM(int currentSeamsRemoved, long[][] E, long[][] M) {
        // insert E values into M
        for (int y = 0; y < inHeight; y++) {
            for (int x = 0; x < inWidth - currentSeamsRemoved; x++) {
                M[y][x] = E[y][x];
            }
        }

        // update M according to forward looking cost formula
        for (int y = 0; y < inHeight; y++) {
            for (int x = 0; x < inWidth - currentSeamsRemoved; x++) {
                if (y > 0) {
                    if (x == 0) {
                        long MCU = M[y - 1][x] + costCU(y, x, currentSeamsRemoved);
                        long MCR = M[y - 1][x + 1] + costCR(y, x, currentSeamsRemoved);
                        M[y][x] += Math.min(MCU, MCR);
                    } else if (x == inWidth - currentSeamsRemoved - 1) {
                        long MCL = M[y - 1][x - 1] + costCL(y, x, currentSeamsRemoved);
                        long MCU = M[y - 1][x] + costCU(y, x, currentSeamsRemoved);
                        M[y][x] += Math.min(MCL, MCU);
                    } else {
                        long MCL = M[y - 1][x - 1] + costCL(y, x, currentSeamsRemoved);
                        long MCU = M[y - 1][x] + costCU(y, x, currentSeamsRemoved);
                        long MCR = M[y - 1][x + 1] + costCR(y, x, currentSeamsRemoved);
                        M[y][x] += minOfThree(MCL, MCU, MCR);
                    }
                }
            }
        }
    }

    private void calcMatrixE(boolean[][] imageMask, int currentSeamsRemoved, long[][] E) {
        // insert energy into the E matrix
        for (int y = 0; y < inHeight; y++) {
            for (int x = 0; x < inWidth - currentSeamsRemoved; x++) {
                E[y][x] = calcGreyScaleForPx(y, I[y][x]);
            }
        }

        // update E matrix according to difference magnitudes and image mask
        for (int y = 0; y < inHeight; y++) {
            for (int x = 0; x < inWidth - currentSeamsRemoved; x++) {
                // decided whether to apply forward or backward difference calculation - using L1 norm
                if ((x == inWidth - currentSeamsRemoved - 1) && (y == inHeight - 1)) {
                    E[y][x] = Math.abs(calcGreyScaleForPx(y, I[y][x - 1]) - E[y][x]) + Math.abs(calcGreyScaleForPx(y - 1, I[y - 1][x]) - E[y][x]);
                } else if (x == inWidth - currentSeamsRemoved - 1) {
                    E[y][x] = Math.abs(calcGreyScaleForPx(y, I[y][x - 1]) - E[y][x]) + Math.abs(E[y + 1][x] - E[y][x]);
                } else if (y == inHeight - 1) {
                    E[y][x] = Math.abs(E[y][x + 1] - E[y][x]) + Math.abs(calcGreyScaleForPx(y - 1, I[y - 1][x]) - E[y][x]);
                } else {
                    E[y][x] = Math.abs(E[y][x + 1] - E[y][x]) + Math.abs(E[y + 1][x] - E[y][x]);
                }

                // add image mask if pixel was marked by the user
                if (imageMask[y][x]) {
                    E[y][x] += Integer.MAX_VALUE;
                }
            }
        }
    }

    private long minOfThree(long a, long b, long c) {
        long minTempA = Math.min(a, b);
        long minTempB = Math.min(b, c);
        return Math.min(minTempA, minTempB);
    }

    private long costCR(int y, int x, int seamsRemoved) {
        long cost = 0;

        if (x == 0) {
            cost += Math.abs(calcGreyScaleForPx(y - 1, I[y - 1][x]) - calcGreyScaleForPx(y, I[y][x + 1]));
        } else if (x == inWidth - seamsRemoved - 1) {
            cost += Integer.MAX_VALUE;  // WHY? THIS CAN'T BE
        } else {
            cost += Math.abs(calcGreyScaleForPx(y, I[y][x + 1]) - calcGreyScaleForPx(y, I[y][x - 1]));
            cost += Math.abs(calcGreyScaleForPx(y - 1, I[y - 1][x]) - calcGreyScaleForPx(y, I[y][x + 1]));
        }

        return cost;
    }

    private long costCL(int y, int x, int seamsRemoved) {
        long cost = 0;

        if (x == 0) {
            cost += Integer.MAX_VALUE; // WHY? THIS CAN'T BE
        } else if (x == inWidth - seamsRemoved - 1) {
            cost += Math.abs(calcGreyScaleForPx(y - 1, I[y - 1][x]) - calcGreyScaleForPx(y, I[y][x - 1]));
        } else {
            cost += Math.abs(calcGreyScaleForPx(y, I[y][x + 1]) - calcGreyScaleForPx(y, I[y][x - 1]));
            cost += Math.abs(calcGreyScaleForPx(y - 1, I[y - 1][x]) - calcGreyScaleForPx(y, I[y][x - 1]));
        }

        return cost;
    }

    private long costCU(int y, int x, int seamsRemoved) {
        long cost = 0;

        if (x != 0 && x != inWidth - seamsRemoved - 1) {
            cost += Math.abs(calcGreyScaleForPx(y, I[y][x + 1]) - calcGreyScaleForPx(y, I[y][x - 1]));
        }

        return cost;
    }

    private int calcGreyScaleForPx(int y, int x) {
        int r = rgbWeights.redWeight;
        int g = rgbWeights.greenWeight;
        int b = rgbWeights.blueWeight;
        Color c = new Color(workingImage.getRGB(x, y));

        int red = r * c.getRed();
        int green = g * c.getGreen();
        int blue = b * c.getBlue();
        return (red + green + blue) / rgbWeights.weightsAmount;
    }

    public BufferedImage resize() {
        return resizeOp.resize();
    }

    private BufferedImage reduceImageWidth() {
        BufferedImage ans = newEmptyOutputSizedImage();

        for (int y = 0; y < outHeight; y++) {
            for (int x = 0; x < outWidth; x++) {
                Color c = new Color(workingImage.getRGB(I[y][x], y));
                ans.setRGB(x, y, c.getRGB());
            }
        }

        return ans;
    }

    private BufferedImage increaseImageWidth() {
        BufferedImage ans = newEmptyOutputSizedImage();

        // we copy the original image into the output image
        for (int y = 0; y < inHeight; y++) {
            for (int x = 0; x < inWidth; x++) {
                Color c = new Color(workingImage.getRGB(x, y));
                ans.setRGB(x, y, c.getRGB());
            }
        }

        // for each pixel that is part of a seam
        for (int y = 0; y < inHeight; y++) {
            for (int x = inWidth - numOfSeams; x < inWidth; x++) {
                // we shift the row right starting from this pixel in the new image
                for (int k = outWidth - 2; k >= I[y][x]; k--) {
                    Color c = new Color(ans.getRGB(k, y));
                    ans.setRGB(k + 1, y, c.getRGB());
                }
                // we update the mapping matrix according to the current shift that was made
                for (int k = x; k < inWidth; k++) {
                    if (I[y][x] < I[y][k]) {
                        I[y][k]++;
                    }
                }
            }
        }

        return ans;
    }

    public BufferedImage showSeams(int seamColorRGB) {
        BufferedImage ans = duplicateWorkingImage();

        for (int y = 0; y < inHeight; y++) {
            for (int x = inWidth - numOfSeams; x < inWidth; x++) {
                Color c = new Color(seamColorRGB);
                ans.setRGB(I[y][x], y, c.getRGB());
            }
        }

        return ans;
    }

    public boolean[][] getMaskAfterSeamCarving() {
        boolean[][] newImageMask = new boolean[outHeight][outWidth];

        // check whether the image was reduced or increased in size
        if (imageSizeShouldBeReduced()) {
            
            // we copy the original image mask values into their new corresponding pixels
            for (int y = 0; y < outHeight; y++) {
                for (int x = 0; x < outWidth; x++) {
                    newImageMask[y][x] = imageMask[y][I[y][x]];
                }
            }
            
        } else {
            
            // copy the original image mask into the new image mask
            for (int y = 0; y < inHeight; y++) {
                for (int x = 0; x < inWidth; x++) {
                    newImageMask[y][x] = imageMask[y][x];
                }
            }

            // for each pixel that is part of a seam
            for (int y = 0; y < inHeight; y++) {
                for (int x = inWidth - numOfSeams; x < inWidth; x++) {
                    // we shift the row right starting from this pixel in the new image
                    // we don't need to fix the mapping Matrix since it has already been updated in IncreaseImageWidth method
                    for (int k = outWidth - 2; k >= I[y][x]; k--) {
                        newImageMask[y][k+1] = newImageMask[y][k];
                    }
                }
            }
        }

        return newImageMask;
    }

    private boolean imageSizeShouldBeReduced() {
        return inWidth > outWidth;
    }
}
