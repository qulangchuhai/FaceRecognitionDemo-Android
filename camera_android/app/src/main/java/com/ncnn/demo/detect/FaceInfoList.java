package com.ncnn.demo.detect;

import java.util.ArrayList;

public class FaceInfoList {
    private ArrayList<FaceInfo> faceList;

    public FaceInfoList(ArrayList<FaceInfo> faceList) {
        this.faceList = faceList;
    }

    public ArrayList<FaceInfo> getFaceList() {
        return faceList;
    }

    public void setFaceList(ArrayList<FaceInfo> faceList) {
        this.faceList = faceList;
    }

    public static class FaceInfo {
        private FaceArea faceArea;
        private ArrayList<FacePointInfo> pointList;

        public FaceInfo(FaceArea faceArea, ArrayList<FacePointInfo> pointList) {
            this.faceArea = faceArea;
            this.pointList = pointList;
        }

        public FaceArea getFaceArea() {
            return faceArea;
        }

        public void setFaceArea(FaceArea faceArea) {
            this.faceArea = faceArea;
        }

        public ArrayList<FacePointInfo> getPointList() {
            return pointList;
        }

        public void setPointList(ArrayList<FacePointInfo> pointList) {
            this.pointList = pointList;
        }
    }

    public static class FaceArea {
        private int x;
        private int y;
        private int width;
        private int height;

        public FaceArea(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public int getX() {
            return x;
        }

        public void setX(int x) {
            this.x = x;
        }

        public int getY() {
            return y;
        }

        public void setY(int y) {
            this.y = y;
        }

        public int getWidth() {
            return width;
        }

        public void setWidth(int width) {
            this.width = width;
        }

        public int getHeight() {
            return height;
        }

        public void setHeight(int height) {
            this.height = height;
        }
    }
    public static class FacePointInfo {
        private int x;
        private int y;

        public FacePointInfo(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public int getX() {
            return x;
        }

        public void setX(int x) {
            this.x = x;
        }

        public int getY() {
            return y;
        }

        public void setY(int y) {
            this.y = y;
        }
    }

}
