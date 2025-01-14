package com.cc.ccPictureBackend.annotation;

/**
 * @Author cuicui
 * @Description
 */
import java.net.URL;

public class test {
    public static void main(String[] args) throws Exception {
        String urlString = "https://project-picture-1311982167.cos.ap-shanghai.myqcloud.com/public/1875064498386706434/2025-01-14_VSsQhZ1mN2Cjx4vB_thumbnail.jpg";
        URL url = new URL(urlString);
        String path = url.getPath();
        System.out.println(path);
    }
}
