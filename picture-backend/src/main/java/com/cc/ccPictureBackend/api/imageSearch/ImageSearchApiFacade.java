package com.cc.ccPictureBackend.api.imageSearch;

import com.cc.ccPictureBackend.api.imageSearch.model.ImageSearchResult;
import com.cc.ccPictureBackend.api.imageSearch.sub.GetImageFirstUrlApi;
import com.cc.ccPictureBackend.api.imageSearch.sub.GetImagePageUrlApi;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author cuicui
 * @Description
 * 门面模式：通过提供一个统一的接口来简化多个接口的调用
 */
public class ImageSearchApiFacade {

    public static List<ImageSearchResult> searchImage(String imageUrl){
        String imagePageUrl = GetImagePageUrlApi.getImagePageUrl(imageUrl);
        String imageFirstUrl = GetImageFirstUrlApi.getImageFirstUrl(imagePageUrl);
        List<ImageSearchResult> imageSearchResults = new ArrayList<ImageSearchResult>();
        return imageSearchResults;
    }
}
