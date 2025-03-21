package com.gcq.picture.manager.picture;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.gcq.picture.annotation.SaSpaceCheckPermission;
import com.gcq.picture.common.ErrorCode;
import com.gcq.picture.constant.FileConstant;
import com.gcq.picture.exception.BusinessException;
import com.gcq.picture.manager.CosManager;
import com.gcq.picture.model.dto.picture.UploadPictureResult;
import com.gcq.picture.model.entity.Picture;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.CIObject;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.qcloud.cos.model.ciModel.persistence.ProcessResults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;

@Slf4j
public abstract class PictureUploadTemplate {

    @Resource
    private CosManager cosManager;





    /**
     * 上传图片
     *
     * @param resource    文件
     * @param uploadPathPrefix 上传路径前缀
     * @return
     */

    public UploadPictureResult uploadPicture(Object resource, String uploadPathPrefix) {
        // 校验图片
         validPicture(resource);
        // 图片上传地址
        String uuid = RandomUtil.randomString(8);
        String originFilename = getOriginalFilename(resource);
        String uploadFilename = String.format("%s_%s.%s", DateUtil.formatDate(new Date()), uuid,
                FileUtil.getSuffix(originFilename));
        String uploadPath = String.format("/%s/%s", uploadPathPrefix, uploadFilename);
        File file = null;
        try {
            // 创建临时文件
            file = File.createTempFile(uploadPath, null);
            makeFile(resource, file);
            // 上传图片
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, file);
            String imageAve = cosManager.getImageAve(uploadPath);
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
            ProcessResults processResults = putObjectResult.getCiUploadResult().getProcessResults();
            List<CIObject> objectList = processResults.getObjectList();
            // 删除临时文件
            if (objectList != null) {
                CIObject ciObject = objectList.get(0);
                CIObject thumbCiObject = objectList.get(1);
                cosManager.deleteObject(uploadPath);
                return buildResult(originFilename,ciObject,thumbCiObject,imageAve);
            }
            // 封装返回结果
            return buildResult(uploadPath,imageInfo,originFilename,file,imageAve);
        } catch (Exception e) {
            log.error("图片上传到对象存储失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        } finally {
            this.deleteTempFile(file);
        }
    }



    protected abstract void makeFile(Object resource, File file) throws IOException;

    protected abstract String getOriginalFilename(Object resource) ;

    protected abstract void validPicture(Object resource);


    /**
     *
     * @param originFilename
     * @param compressedCiObject
     * @return
     */
    private UploadPictureResult buildResult(String originFilename, CIObject compressedCiObject,CIObject thumbCiObject,String imageAve) {
        UploadPictureResult uploadPictureResult = new UploadPictureResult();
        int picWidth = compressedCiObject.getWidth();
        int picHeight = compressedCiObject.getHeight();
        double picScale = NumberUtil.round(picWidth * 1.0 / picHeight, 2).doubleValue();
        uploadPictureResult.setPicName(FileUtil.mainName(originFilename));
        uploadPictureResult.setPicWidth(picWidth);
        uploadPictureResult.setPicHeight(picHeight);
        uploadPictureResult.setPicScale(picScale);
        uploadPictureResult.setPicFormat(compressedCiObject.getFormat());
        uploadPictureResult.setPicSize(compressedCiObject.getSize().longValue());
        uploadPictureResult.setThumbnailUrl(FileConstant.COS_HOST + "/" + thumbCiObject.getKey());
        uploadPictureResult.setPicColor(imageAve);
        // 设置图片为压缩后的地址
        uploadPictureResult.setUrl(FileConstant.COS_HOST + "/" + compressedCiObject.getKey());
        return uploadPictureResult;
    }

    /**
     *
     * @param uploadPath
     * @param imageInfo
     * @param originFilename
     * @param file
     * @return
     */
    protected  UploadPictureResult buildResult(String uploadPath, ImageInfo imageInfo, String originFilename, File file,String imageAve) {
        UploadPictureResult uploadPictureResult = new UploadPictureResult();
        int picWidth = imageInfo.getWidth();
        int picHeight = imageInfo.getHeight();
        double picScale = NumberUtil.round(picWidth * 1.0 / picHeight, 2).doubleValue();
        uploadPictureResult.setPicName(FileUtil.mainName(originFilename));
        uploadPictureResult.setPicWidth(picWidth);
        uploadPictureResult.setPicHeight(picHeight);
        uploadPictureResult.setPicScale(picScale);
        uploadPictureResult.setPicFormat(imageInfo.getFormat());
        uploadPictureResult.setPicSize(FileUtil.size(file));
        uploadPictureResult.setPicColor(imageAve);
        uploadPictureResult.setUrl(FileConstant.COS_HOST + "/" + uploadPath);
        return uploadPictureResult;
    }

    /**
     * 删除临时文件
     */
    public void deleteTempFile(File file) {
        if (file == null) {
            return;
        }
        // 删除临时文件
        boolean deleteResult = file.delete();
        if (!deleteResult) {
            log.error("file delete error, filepath = {}", file.getAbsolutePath());
        }
    }

}
