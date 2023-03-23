package com.huohaodong.octopus.broker.protocol.mqtt.handler;

import com.huohaodong.octopus.broker.config.BrokerProperties;
import com.huohaodong.octopus.broker.server.cluster.ClusterEventManager;
import com.huohaodong.octopus.broker.server.metric.annotation.ReceivedMetric;
import com.huohaodong.octopus.broker.server.metric.aspect.StatsCollector;
import com.huohaodong.octopus.broker.store.message.*;
import com.huohaodong.octopus.broker.store.session.ChannelManager;
import com.huohaodong.octopus.broker.store.session.SessionManager;
import com.huohaodong.octopus.broker.store.subscription.Subscription;
import com.huohaodong.octopus.broker.store.subscription.SubscriptionManager;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;

@Slf4j
@AllArgsConstructor
@Component
public class MqttPublishHandler implements MqttPacketHandler<MqttPublishMessage> {

    private final ChannelManager channelManager;

    private final SessionManager sessionManager;

    private final SubscriptionManager subscriptionManager;

    private final PublishMessageManager publishMessageManager;

    private final RetainMessageManager retainMessageManager;

    private final MessageIdGenerator idGenerator;

    private final ClusterEventManager clusterEventManager;

    private final BrokerProperties brokerProperties;

    private final StatsCollector statsCollector;

    @Override
    @ReceivedMetric
    public void doProcess(ChannelHandlerContext ctx, MqttPublishMessage msg) {
        MqttQoS QoS = msg.fixedHeader().qosLevel();
        byte[] payload = new byte[msg.payload().readableBytes()];
        msg.payload().getBytes(msg.payload().readerIndex(), payload);
        if (QoS.equals(MqttQoS.AT_MOST_ONCE)) {
            sendPublishMessage(msg.variableHeader().topicName(),
                    msg.fixedHeader().qosLevel(),
                    payload,
                    false,
                    false);
        } else if (QoS.equals(MqttQoS.AT_LEAST_ONCE)) {
            sendPublishMessage(msg.variableHeader().topicName(), msg.fixedHeader().qosLevel(), payload, false, false);
            sendPubAckMessage(ctx, msg.variableHeader().packetId());
        } else if (QoS.equals(MqttQoS.EXACTLY_ONCE)) {
            sendPublishMessage(msg.variableHeader().topicName(), msg.fixedHeader().qosLevel(), payload, false, false);
            sendPubRecMessage(ctx, msg.variableHeader().packetId());
        }

        if (msg.fixedHeader().isRetain()) {
            if (payload.length == 0) {
                retainMessageManager.remove(msg.variableHeader().topicName());
            } else {
                RetainMessage retainMessage = new RetainMessage(msg.variableHeader().topicName(), QoS, payload);
                retainMessageManager.put(msg.variableHeader().topicName(), retainMessage);
            }
        }

        clusterEventManager.broadcastToPublish(new PublishMessage(
                brokerProperties.getId(),
                0,
                msg.variableHeader().topicName(),
                MqttQoS.AT_MOST_ONCE,
                payload,
                MqttMessageType.PUBLISH));
    }

    private void sendPubAckMessage(ChannelHandlerContext ctx, int messageId) {
        MqttPubAckMessage pubAckMessage = (MqttPubAckMessage) MqttMessageFactory.newMessage(
                new MqttFixedHeader(MqttMessageType.PUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                MqttMessageIdVariableHeader.from(messageId), null);
        ctx.channel().writeAndFlush(pubAckMessage);
        statsCollector.getDeltaTotalSent().incrementAndGet();
    }

    private void sendPubRecMessage(ChannelHandlerContext ctx, int messageId) {
        MqttMessage pubRecMessage = MqttMessageFactory.newMessage(
                new MqttFixedHeader(MqttMessageType.PUBREC, false, MqttQoS.AT_MOST_ONCE, false, 0),
                MqttMessageIdVariableHeader.from(messageId), null);
        ctx.channel().writeAndFlush(pubRecMessage);
        statsCollector.getDeltaTotalSent().incrementAndGet();
    }

    private void sendPublishMessage(String topic, MqttQoS QoS, byte[] messageBytes, boolean isRetain, boolean isDup) {
        Collection<Subscription> subscriptions = subscriptionManager.getAllMatched(topic);
        if (subscriptions == null) {
            return;
        }
        subscriptions.forEach(subscription -> {
            if (sessionManager.contains(subscription.getClientId())) {
                statsCollector.getDeltaTotalSent().incrementAndGet();
                MqttQoS respQoS = MqttQoS.valueOf(Math.min(QoS.value(), subscription.getQoS().value()));
                MqttPublishMessage publishMessage = null;
                if (respQoS == MqttQoS.AT_MOST_ONCE) {
                    publishMessage = (MqttPublishMessage) MqttMessageFactory.newMessage(
                            new MqttFixedHeader(MqttMessageType.PUBLISH, isDup, respQoS, isRetain, 0),
                            new MqttPublishVariableHeader(topic, 0), Unpooled.buffer().writeBytes(messageBytes));
                }
                if (respQoS == MqttQoS.AT_LEAST_ONCE) {
                    int messageId = idGenerator.acquireId();
                    publishMessage = (MqttPublishMessage) MqttMessageFactory.newMessage(
                            new MqttFixedHeader(MqttMessageType.PUBLISH, isDup, respQoS, isRetain, 0),
                            new MqttPublishVariableHeader(topic, messageId), Unpooled.buffer().writeBytes(messageBytes));
                    publishMessageManager.put(new PublishMessage(subscription.getClientId(), messageId, topic, respQoS, messageBytes, MqttMessageType.PUBLISH));
                }
                if (respQoS == MqttQoS.EXACTLY_ONCE) {
                    int messageId = idGenerator.acquireId();
                    publishMessage = (MqttPublishMessage) MqttMessageFactory.newMessage(
                            new MqttFixedHeader(MqttMessageType.PUBLISH, isDup, respQoS, isRetain, 0),
                            new MqttPublishVariableHeader(topic, messageId), Unpooled.buffer().writeBytes(messageBytes));
                    publishMessageManager.put(new PublishMessage(subscription.getClientId(), messageId, topic, respQoS, messageBytes, MqttMessageType.PUBREL));
                }
                if (publishMessage != null) {
                    Channel channel = channelManager.getChannel(subscription.getClientId());
                    if (channel != null) {
                        channel.writeAndFlush(publishMessage);
                    }
                }
            }
        });
    }
}