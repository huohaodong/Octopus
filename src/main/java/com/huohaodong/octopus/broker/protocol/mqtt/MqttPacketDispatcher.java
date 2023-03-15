package com.huohaodong.octopus.broker.protocol.mqtt;

import com.huohaodong.octopus.broker.protocol.mqtt.handler.*;
import com.huohaodong.octopus.broker.store.session.Session;
import com.huohaodong.octopus.broker.store.session.SessionManager;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.UnsupportedMessageTypeException;
import io.netty.handler.codec.mqtt.*;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@AllArgsConstructor
@Component
@ChannelHandler.Sharable
public class MqttPacketDispatcher extends SimpleChannelInboundHandler<MqttMessage> {
    private final SessionManager sessionManager;

    private final MqttConnectHandler mqttConnectHandler;

    private final MqttPublishHandler mqttPublishHandler;

    private final MqttPubAckHandler mqttPubAckHandler;

    private final MqttPubRecHandler mqttPubRecHandler;

    private final MqttPubRelHandler mqttPubRelHandler;

    private final MqttPubCompHandler mqttPubCompHandler;

    private final MqttSubscribeHandler mqttSubscribeHandler;

    private final MqttUnSubscribeHandler mqttUnSubscribeHandler;

    private final MqttPingHandler mqttPingHandler;

    private final MqttDisconnectHandler mqttDisconnectHandler;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, MqttMessage msg) throws Exception {
        MqttMessageType msgType = msg.fixedHeader().messageType();
        log.info("received {} message", msgType);
        switch (msgType) {
            case CONNECT:
                mqttConnectHandler.doProcess(ctx, (MqttConnectMessage) msg);
                break;
            case PUBLISH:
                mqttPublishHandler.doProcess(ctx, (MqttPublishMessage) msg);
                break;
            case PUBACK:
                mqttPubAckHandler.doProcess(ctx, (MqttPubAckMessage) msg);
                break;
            case PUBREC:
                mqttPubRecHandler.doProcess(ctx, msg);
                break;
            case PUBREL:
                mqttPubRelHandler.doProcess(ctx, msg);
                break;
            case PUBCOMP:
                mqttPubCompHandler.doProcess(ctx, msg);
                break;
            case SUBSCRIBE:
                mqttSubscribeHandler.doProcess(ctx, (MqttSubscribeMessage) msg);
                break;
            case UNSUBSCRIBE:
                mqttUnSubscribeHandler.doProcess(ctx, (MqttUnsubscribeMessage) msg);
                break;
            case PINGREQ:
                mqttPingHandler.doProcess(ctx, msg);
                break;
            case DISCONNECT:
                mqttDisconnectHandler.doProcess(ctx, msg);
                break;
            case AUTH:
                break;
            default:
                ctx.channel().close();
                throw new UnsupportedMessageTypeException(msg.decoderResult().cause(), MqttMessageType.class);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (cause instanceof IOException) {
            log.error("IOException, close remote connection");
            ctx.close();
        } else {
            super.exceptionCaught(ctx, cause);
        }
    }

    /* 心跳包 */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent idleStateEvent = (IdleStateEvent) evt;
            // TODO AttributeKey 改为配置文件，目前是硬编码
            String clientId = (String) ctx.channel().attr(AttributeKey.valueOf("CLIENT_ID")).get();
            if (idleStateEvent.state() == IdleState.ALL_IDLE) {
                log.info("heartbeat timeout, close channel");
                // TODO 测试遗嘱消息
                if (sessionManager.containsKey(clientId)) {
                    Session session = sessionManager.get(clientId);
                    if (session.getWillMessage() != null) {
                        mqttPublishHandler.doProcess(ctx, session.getWillMessage());
                    }
                }
                ctx.close();
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }
}
