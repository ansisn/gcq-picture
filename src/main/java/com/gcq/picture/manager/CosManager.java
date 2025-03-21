package com.gcq.picture.manager;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import com.gcq.picture.common.ErrorCode;
import com.gcq.picture.exception.BusinessException;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.*;
import com.gcq.picture.config.CosClientConfig;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Resource;
import com.qcloud.cos.model.ciModel.persistence.PicOperations;
import com.qcloud.cos.model.ciModel.persistence.PicOperations.Rule;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Component;

/**
 * Cos 对象存储操作
 *
 * @author <a href="https://github.com/ligcq">程序员gcq</a>
 * 
 */
@Component
public class CosManager {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private COSClient cosClient;

    /**
     * 上传对象
     *
     * @param key 唯一键
     * @param localFilePath 本地文件路径
     * @return
     */
    public PutObjectResult putObject(String key, String localFilePath) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key,
                new File(localFilePath));
        return cosClient.putObject(putObjectRequest);
    }

    /**
     * 上传对象
     *
     * @param key 唯一键
     * @param file 文件
     * @return
     */
    public PutObjectResult putObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key,
                file);
        return cosClient.putObject(putObjectRequest);
    }


    /**
     * 上传对象（附带图片信息）
     *
     * @param key  唯一键
     * @param file 文件
     */
    public PutObjectResult putPictureObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key,
                file);
        // 对图片进行处理（获取基本信息也被视作为一种处理）
        PicOperations picOperations = new PicOperations();
        // 1 表示返回原图信息
        picOperations.setIsPicInfo(1);
        //进行图片优化
        List<Rule> rules = new ArrayList<>();
        Rule rule = new Rule();
        String webpKey = FileUtil.mainName(key) + ".webp";
        rule.setBucket(cosClientConfig.getBucket());
        rule.setRule("imageMogr2/format/webp");
        rule.setFileId(webpKey);
        rules.add(rule);
        picOperations.setRules(rules);
        //新增缩略图
        Rule thumbRule = new Rule();
        String thumbKey = FileUtil.mainName(key) + "_thumbnail." + FileUtil.getSuffix(key);
        thumbRule.setBucket(cosClientConfig.getBucket());
        thumbRule.setRule(String.format("imageMogr2/thumbnail/%sx%s",128,128));
        thumbRule.setFileId(thumbKey);
        rules.add(thumbRule);
        // 构造处理参数
        putObjectRequest.setPicOperations(picOperations);
        return cosClient.putObject(putObjectRequest);
    }



    /**
     * 获取图片主色调
     *
     * @param key 文件 key
     * @return 图片主色调
     */
    public String getImageAve(String key) {
        GetObjectRequest getObj = new GetObjectRequest(cosClientConfig.getBucket(), key);
        String rule = "imageAve";
        getObj.putCustomQueryParameter(rule, null);
        COSObject object = cosClient.getObject(getObj);
        COSObjectInputStream objectContent = object.getObjectContent();
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            CloseableHttpResponse httpResponse = httpClient.execute(objectContent.getHttpRequest());
            String response = EntityUtils.toString(httpResponse.getEntity());
            return JSONUtil.parseObj(response).getStr("RGB");
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
    }


    /**
     * 下载对象
     *
     * @param key 唯一键
     */
    public COSObject getObject(String key) {
        GetObjectRequest getObjectRequest = new GetObjectRequest(cosClientConfig.getBucket(), key);
        return cosClient.getObject(getObjectRequest);
    }

    public void deleteObject(String key) {
        cosClient.deleteObject(cosClientConfig.getBucket(), key);
    }

}
