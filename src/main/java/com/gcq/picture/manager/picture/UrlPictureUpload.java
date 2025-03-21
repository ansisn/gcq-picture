package com.gcq.picture.manager.picture;

import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import com.gcq.picture.common.ErrorCode;
import com.gcq.picture.exception.ThrowUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.regex.Pattern;

@Service
public class UrlPictureUpload extends PictureUploadTemplate{
    private static final long MAX_FILE_SIZE = 2 * 1024 * 1024; // 2MB
    private boolean isValidUrl(String fileUrl) {
        // 简单的URL格式校验
        String urlRegex = "^(https?://)?([\\da-z.-]+)\\.([a-z.]{2,6})([/\\w .-]*)*/?$";
        Pattern pattern = Pattern.compile(urlRegex);
        return pattern.matcher(fileUrl).matches();
    }

    @Override
    protected void makeFile(Object resource, File file) {
        String fileUrl = (String) resource;
        HttpUtil.downloadFile(fileUrl,file);
    }

    @Override
    protected String getOriginalFilename(Object resource) {
        String fileUrl = (String) resource;
       // return  FileUtil.mainName(fileUrl); mainName方法没有文件名字,需要返回后缀
        return  FileUtil.getName(fileUrl);
    }

    @Override
    protected void validPicture(Object resource) {
        String fileUrl = (String) resource;
        ThrowUtils.throwIf(fileUrl == null || fileUrl.trim().isEmpty(), ErrorCode.PARAMS_ERROR, "URL不能为空");

        // 校验URL格式
        ThrowUtils.throwIf(!isValidUrl(fileUrl), ErrorCode.PARAMS_ERROR, "URL格式不正确");

        // 校验URL请求头协议，仅支持HTTP/HTTPS
        ThrowUtils.throwIf(!fileUrl.startsWith("http://") && !fileUrl.startsWith("https://"), ErrorCode.PARAMS_ERROR, "URL协议错误");

        // 发送HEAD请求验证文件是否存在
        HttpResponse response = null;
        try {
            response = HttpUtil.createRequest(Method.HEAD, fileUrl).execute();
           // response = HttpUtil.createGet(fileUrl).execute();

            // 检查HTTP状态码
            if (response.getStatus() != HttpURLConnection.HTTP_OK) {
                throw new IllegalArgumentException("URL 不存在 或者 行不通");
            }

            // 文件存在，文件类型校验
            String contentType = response.header("Content-Type");
            if (contentType == null || !contentType.startsWith("image/")) {
                throw new IllegalArgumentException("文件格式异常");
            }
           //todo 请求头不包含 Content-Length，无法校验文件大小
            // 文件存在，文件大小校验
            String contentLength = response.header("Content-Length");
//            if (contentLength == null || contentLength.isEmpty()) {
//                throw new IllegalArgumentException("文件大小过小或不存在");
//            }
//
//            long fileSize = Long.parseLong(contentLength);
//            if (fileSize <= 0) {
//                throw new IllegalArgumentException("文件大小异常");
//            }
//
//            // 限制文件大小为2MB
//            if (fileSize > MAX_FILE_SIZE) {
//                throw new IllegalArgumentException("文件大小过大");
//            }

        } catch (Exception e) {
            throw new IllegalArgumentException("Error while accessing the URL: " + e.getMessage());
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }



}
