package com.gcq.picture.api.imagesearch.facadepattern;

import com.gcq.picture.api.imagesearch.model.ImageSearchResult;
import com.gcq.picture.api.imagesearch.sub.GetImageFirstUrlApi;
import com.gcq.picture.api.imagesearch.sub.GetImageListApi;
import com.gcq.picture.api.imagesearch.sub.GetImagePageUrlApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class ImageSearchApiFacade {

    /**
     * 搜索图片
     *
     * @param imageUrl
     * @return
     */
    public static List<ImageSearchResult> searchImage(String imageUrl) {
        String imagePageUrl = GetImagePageUrlApi.getImagePageUrl(imageUrl);
        String imageFirstUrl = GetImageFirstUrlApi.getImageFirstUrl(imagePageUrl);
        List<ImageSearchResult> imageList = GetImageListApi.getImageList(imageFirstUrl);
        return imageList;
    }


}
