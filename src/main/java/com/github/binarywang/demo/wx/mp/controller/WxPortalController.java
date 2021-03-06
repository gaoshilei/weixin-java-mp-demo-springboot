package com.github.binarywang.demo.wx.mp.controller;

import me.chanjar.weixin.mp.api.WxMpMessageRouter;
import me.chanjar.weixin.mp.api.WxMpTemplateMsgService;
import me.chanjar.weixin.mp.api.impl.WxMpTemplateMsgServiceImpl;
import me.chanjar.weixin.mp.bean.template.WxMpTemplateMessage;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.bean.message.WxMpXmlMessage;
import me.chanjar.weixin.mp.bean.message.WxMpXmlOutMessage;

import me.chanjar.weixin.mp.bean.template.WxMpTemplateData;
import me.chanjar.weixin.common.error.WxErrorException;

/**
 * @author Binary Wang(https://github.com/binarywang)
 */
@RestController
@RequestMapping("/wx/portal/{appid}")
public class WxPortalController {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private WxMpService wxService;

    private WxMpMessageRouter messageRouter;

    @Autowired
    public WxPortalController(WxMpService wxService, WxMpMessageRouter messageRouter) {
        this.wxService = wxService;
        this.messageRouter = messageRouter;
    }

    @GetMapping(produces = "text/plain;charset=utf-8")
    public String authGet(@PathVariable String appid,
                          @RequestParam(name = "signature", required = false) String signature,
                          @RequestParam(name = "timestamp", required = false) String timestamp,
                          @RequestParam(name = "nonce", required = false) String nonce,
                          @RequestParam(name = "echostr", required = false) String echostr) {

        this.logger.info("\n接收到来自微信服务器的认证消息：[{}, {}, {}, {}]", signature,
            timestamp, nonce, echostr);
        if (StringUtils.isAnyBlank(signature, timestamp, nonce, echostr)) {
            throw new IllegalArgumentException("请求参数非法，请核实!");
        }

        if (!this.wxService.switchover(appid)) {
            throw new IllegalArgumentException(String.format("未找到对应appid=[%s]的配置，请核实！", appid));
        }

        if (wxService.checkSignature(timestamp, nonce, signature)) {
            return echostr;
        }

        return "非法请求";
    }

    @PostMapping(produces = "application/xml; charset=UTF-8")
    public String post(@PathVariable String appid,
                       @RequestBody String requestBody,
                       @RequestParam("signature") String signature,
                       @RequestParam("timestamp") String timestamp,
                       @RequestParam("nonce") String nonce,
                       @RequestParam("openid") String openid,
                       @RequestParam(name = "encrypt_type", required = false) String encType,
                       @RequestParam(name = "msg_signature", required = false) String msgSignature) {
        this.logger.info("\n接收微信请求：[openid=[{}], [signature=[{}], encType=[{}], msgSignature=[{}],"
                + " timestamp=[{}], nonce=[{}], requestBody=[\n{}\n] ",
            openid, signature, encType, msgSignature, timestamp, nonce, requestBody);

        if (!this.wxService.switchover(appid)) {
            throw new IllegalArgumentException(String.format("未找到对应appid=[%s]的配置，请核实！", appid));
        }

        if (!this.wxService.checkSignature(timestamp, nonce, signature)) {
            throw new IllegalArgumentException("非法请求，可能属于伪造的请求！");
        }

        String out = null;

        WxMpXmlMessage xmlMsg = WxMpXmlMessage.fromXml(requestBody);
        this.logger.debug("\nxmlMsg===>>>{}", xmlMsg);
        if (encType == null) {
            // 明文传输的消息
            this.logger.debug("\n");

            try {
                if (xmlMsg.getContent()!=null&&xmlMsg.getContent().equals("模板消息")) {
                    this.logger.debug("\n收到消息，需要回复!");
                    this.testSendTemplateMessage(openid);
                } else {
                    WxMpXmlMessage inMessage = WxMpXmlMessage.fromXml(requestBody);
                    this.logger.debug("\n自动回复消息===>>>{}", inMessage);
                    WxMpXmlOutMessage outMessage = this.route(inMessage);
                    if (outMessage == null) {
                        return "";
                    }
                    out = outMessage.toXml();
                }
            } catch (WxErrorException e) {
                e.printStackTrace();
            }
        } else if ("aes".equalsIgnoreCase(encType)) {
            // aes加密的消息
            WxMpXmlMessage inMessage = WxMpXmlMessage.fromEncryptedXml(requestBody, wxService.getWxMpConfigStorage(),
                timestamp, nonce, msgSignature);
            this.logger.debug("\n消息解密后内容为：\n{} ", inMessage.toString());
            WxMpXmlOutMessage outMessage = this.route(inMessage);
            if (outMessage == null) {
                return "";
            }

            out = outMessage.toEncryptedXml(wxService.getWxMpConfigStorage());
        }


        this.logger.debug("\n组装回复信息：{}", out);
        return out;
    }

    private WxMpXmlOutMessage route(WxMpXmlMessage message) {
        try {
            return this.messageRouter.route(message);
        } catch (Exception e) {
            this.logger.error("路由消息时出现异常！", e);
        }

        return null;
    }

    private void testSendTemplateMessage(String openid) throws WxErrorException {
        WxMpTemplateMessage tm = WxMpTemplateMessage.builder()
            .toUser(openid)
            .templateId("P89atWk3Igr2uSfi8sxhiFQQfG1-xI8sgYe-K6bdvKI")
            .build();
        tm.addData(
            new WxMpTemplateData("first", "您好，您的保单已承保成功。", "#000000"));
        tm.addData(
            new WxMpTemplateData("keyword1", "珠江汇赢一号终身寿险（万能型）（2015版）", "#1890ff"));
        tm.addData(
            new WxMpTemplateData("keyword2", "王大锤", "#000000"));
        tm.addData(
            new WxMpTemplateData("keyword3", "38167478149867809", "#000000"));
        tm.addData(
            new WxMpTemplateData("keyword4", "2016年8月4日", "#000000"));
        tm.addData(
            new WxMpTemplateData("keyword5", "19238元", "#000000"));
        tm.addData(
            new WxMpTemplateData("remark", "请登陆www.prlife.com.cn下载和确认电子保单，并完成在线回访。", "#000000"));
        String msgId = this.wxService.getTemplateMsgService().sendTemplateMsg(tm);
    }

}
