package com.huohaodong.octopus.broker.protocol.mqtt.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;
import org.springframework.stereotype.Component;

@Component
public class MqttPubCompHandler implements MqttPacketHandler<MqttMessage> {
    @Override
    public void doProcess(ChannelHandlerContext ctx, MqttMessage msg) {

    }
}
