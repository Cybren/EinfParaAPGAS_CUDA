package examples;

import java.io.Serializable;

final class Rectangle implements Serializable {
    private static final long serialVersionUID = 12345L;

    int height, width;

    Rectangle(int height, int width) {
        this.height = height;
        this.width = width;
    }

    int getHeight() {
        return height;
    }

    void incHeight() {
        ++height;
    }

    int getWidth() {
        return width;
    }

    void setWidth(int width) {
        this.width = width;
    }
}
