This project is an implementation of the Seam Carving algorithm in Computer Graphics, which allows the user to resize an image without deforming the main components of the image.
The user inputs an image for resizing, chooses the desired output size, and a few more optional features:
  1. Checkbox for Greyscaling the image
  2. Resizing using the nearest-neighbor or using seam-carving algorithm
  3. Changing the hue of the picture by declaring RGB values
  4. Masking the image (using seam-carving only) - this allows the user to color with a brush the important parts of the picture which the user wants to keep intact.
  5. Show seams buttons allow the user to view the seams chosen by the algorithm for removal/duplication.
  
The algorithm first creates a matrix with the size of the input image and measures the magnitude of each pixel - how much it differs from its surrounding pixels.
If the magnitude is high, this means that the pixel is relatively important and should not be quickly chosen for removal.
After calculating the matrix, the algorithm starts choosing the first seam to remove/duplicate - this is done with dynamic programming, in which we choose the lowest magnitude pixel in the first row and advance towards the last row choosing consecutive pixels.
