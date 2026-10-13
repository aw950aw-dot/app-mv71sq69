package com.generated.app;

public class CarvedFileHolder {
    private final CarvedFile carvedFile;
    private boolean selected;

    public CarvedFileHolder(CarvedFile carvedFile) {
        this.carvedFile = carvedFile;
        this.selected = true; // Default selected
    }

    public CarvedFile getCarvedFile() {
        return carvedFile;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }
}
