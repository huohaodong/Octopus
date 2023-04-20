package com.huohaodong.octopus.common.persistence.entity;

import io.netty.handler.codec.mqtt.MqttQoS;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(
        name = "publish_message",
        schema = "octopus",
        indexes = {
                @Index(name = "idx_message_identity", columnList = "broker_id, client_id, message_id"),
        }
)
public class PublishMessage implements Serializable {
    @Serial
    private static final long serialVersionUID = -1970710271196475976L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "broker_id", length = Constants.BROKER_ID_LENGTH_MAX, nullable = false)
    private String brokerId;

    @Column(name = "client_id", length = Constants.CLIENT_ID_LENGTH_MAX, nullable = false)
    private String clientId;

    @Column(name = "message_id", length = Constants.MESSAGE_ID_LENGTH_MAX, nullable = false)
    private Integer messageId;

    @Column(name = "topic", length = Constants.TOPIC_LENGTH_MAX, nullable = false)
    private String topic;

    @Lob
    @Column(name = "payload", length = Constants.MESSAGE_PAYLOAD_LENGTH_MAX)
    private byte[] payload;

    @Column(name = "qos", nullable = false)
    @Enumerated(EnumType.ORDINAL)
    private MqttQoS qos;

    @CreationTimestamp
    @Column(name = "create_time", updatable = false, nullable = false)
    private LocalDateTime createTime;
}
