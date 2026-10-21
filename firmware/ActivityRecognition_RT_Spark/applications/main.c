#include <rtthread.h>
#include <rtdevice.h>
#include <string.h>
#include <stdio.h>

#include "../rt-thread/components/net/lwip/lwip-2.0.3/src/include/lwip/sockets.h"
#include "../rt-thread/components/net/lwip/lwip-2.0.3/src/include/lwip/inet.h"

#include "../packages/icm20608-v1.0.0/icm20608.h"

/* Directly included drivers used by this project */
#include "../libraries/HAL_Drivers/drv_soft_i2c.c"
#include "../libraries/HAL_Drivers/drv_spi.c"

#include "../libraries/STM32F4xx_HAL/STM32F4xx_HAL_Driver/Src/stm32f4xx_hal_spi.c"

#include "../rt-thread/components/drivers/spi/spi_core.c"
#include "../rt-thread/components/drivers/spi/spi_dev.c"



#define MQTT_BROKER_IP   "192.168.254.100"
#define MQTT_BROKER_PORT 1883
#define MQTT_TOPIC       "device/sensor_data"


/* =========================================================
 * Send all bytes through TCP
 * ========================================================= */
static int mqtt_send_all(
    int sock,
    const unsigned char *data,
    int length)
{
    int total = 0;

    while (total < length)
    {
        int sent = lwip_send(
            sock,
            data + total,
            length - total,
            0
        );

        if (sent <= 0)
        {
            return -1;
        }

        total += sent;
    }

    return 0;
}


/* =========================================================
 * MQTT Remaining Length encoder
 * ========================================================= */
static int mqtt_encode_remaining_length(
    unsigned char *buffer,
    int length)
{
    int index = 0;

    do
    {
        unsigned char encoded =
            (unsigned char)(length % 128);

        length /= 128;

        if (length > 0)
        {
            encoded |= 0x80;
        }

        buffer[index++] = encoded;

    } while (length > 0);

    return index;
}


/* =========================================================
 * Connect to MQTT broker
 * ========================================================= */
static int mqtt_connect_broker(void)
{
    int sock;
    struct sockaddr_in server;

    unsigned char packet[64];
    unsigned char response[8];

    int index = 0;
    int received;


    sock = lwip_socket(
        AF_INET,
        SOCK_STREAM,
        0
    );

    if (sock < 0)
    {
        rt_kprintf(
            "MQTT: socket creation failed\n"
        );

        return -1;
    }


    memset(
        &server,
        0,
        sizeof(server)
    );

    server.sin_family = AF_INET;

    server.sin_port =
        htons(MQTT_BROKER_PORT);

    server.sin_addr.s_addr =
        inet_addr(MQTT_BROKER_IP);


    rt_kprintf(
        "MQTT: connecting to %s:%d...\n",
        MQTT_BROKER_IP,
        MQTT_BROKER_PORT
    );


    if (lwip_connect(
            sock,
            (struct sockaddr *)&server,
            sizeof(server)) < 0)
    {
        rt_kprintf(
            "MQTT: TCP connection failed\n"
        );

        lwip_close(sock);

        return -1;
    }


    rt_kprintf(
        "MQTT: TCP connected\n"
    );


    /*
     * MQTT CONNECT packet
     * MQTT version 3.1.1
     * Client ID: rt-spark
     */

    packet[index++] = 0x10;

    /* Remaining length = 20 */
    packet[index++] = 0x14;

    /* Protocol name length */
    packet[index++] = 0x00;
    packet[index++] = 0x04;

    packet[index++] = 'M';
    packet[index++] = 'Q';
    packet[index++] = 'T';
    packet[index++] = 'T';

    /* MQTT 3.1.1 */
    packet[index++] = 0x04;

    /* Clean Session */
    packet[index++] = 0x02;

    /* Keep alive = 60 seconds */
    packet[index++] = 0x00;
    packet[index++] = 60;

    /* Client ID length = 8 */
    packet[index++] = 0x00;
    packet[index++] = 0x08;

    memcpy(
        &packet[index],
        "rt-spark",
        8
    );

    index += 8;


    if (mqtt_send_all(
            sock,
            packet,
            index) < 0)
    {
        rt_kprintf(
            "MQTT: CONNECT send failed\n"
        );

        lwip_close(sock);

        return -1;
    }


    received = lwip_recv(
        sock,
        response,
        sizeof(response),
        0
    );


    if (received < 4)
    {
        rt_kprintf(
            "MQTT: invalid CONNACK\n"
        );

        lwip_close(sock);

        return -1;
    }


    if (response[0] != 0x20 ||
        response[3] != 0x00)
    {
        rt_kprintf(
            "MQTT: broker rejected connection\n"
        );

        lwip_close(sock);

        return -1;
    }


    rt_kprintf(
        "MQTT: broker connected\n"
    );


    return sock;
}


/* =========================================================
 * Publish MQTT QoS 0 message
 * ========================================================= */
static int mqtt_publish(
    int sock,
    const char *payload)
{
    unsigned char packet[256];

    int topic_length =
        strlen(MQTT_TOPIC);

    int payload_length =
        strlen(payload);

    int remaining_length =
        2 +
        topic_length +
        payload_length;

    int index = 0;


    /* PUBLISH, QoS 0 */
    packet[index++] = 0x30;


    index +=
        mqtt_encode_remaining_length(
            &packet[index],
            remaining_length
        );


    /* Topic length */
    packet[index++] =
        (unsigned char)(
            (topic_length >> 8) & 0xFF
        );

    packet[index++] =
        (unsigned char)(
            topic_length & 0xFF
        );


    memcpy(
        &packet[index],
        MQTT_TOPIC,
        topic_length
    );

    index += topic_length;


    memcpy(
        &packet[index],
        payload,
        payload_length
    );

    index += payload_length;


    if (mqtt_send_all(
            sock,
            packet,
            index) < 0)
    {
        return -1;
    }


    return 0;
}


/* =========================================================
 * Main
 * ========================================================= */
int main(void)
{
    icm20608_device_t sensor;

    rt_int16_t ax;
    rt_int16_t ay;
    rt_int16_t az;

    rt_int16_t gx;
    rt_int16_t gy;
    rt_int16_t gz;

    char payload[160];

    int mqtt_socket = -1;


    rt_kprintf(
        "\n====================================\n"
    );

    rt_kprintf(
        " Activity Recognition RT-Spark\n"
    );

    rt_kprintf(
        "====================================\n"
    );


    /*
     * Give RT-Thread startup components
     * time to initialize SPI/WLAN/lwIP.
     */
    rt_thread_mdelay(3000);


    /* =============================
     * Initialize ICM20608
     * ============================= */

    rt_kprintf(
        "Initializing ICM20608...\n"
    );


    sensor =
        icm20608_init("i2c2");


    if (sensor == RT_NULL)
    {
        rt_kprintf(
            "ERROR: ICM20608 initialization failed\n"
        );

        while (1)
        {
            rt_thread_mdelay(1000);
        }
    }


    rt_kprintf(
        "ICM20608 initialized\n"
    );


    /*
     * Give Wi-Fi time to connect.
     *
     * You already successfully joined Wi-Fi
     * using the RT-Thread shell.
     */
    rt_kprintf(
        "Waiting for Wi-Fi/network...\n"
    );


    rt_thread_mdelay(5000);


    /* =============================
     * Main publishing loop
     * ============================= */

    while (1)
    {
        /* -------------------------
         * Connect/reconnect MQTT
         * ------------------------- */

        if (mqtt_socket < 0)
        {
            mqtt_socket =
                mqtt_connect_broker();


            if (mqtt_socket < 0)
            {
                rt_kprintf(
                    "MQTT: retrying in 3 seconds...\n"
                );

                rt_thread_mdelay(3000);

                continue;
            }
        }


        /* -------------------------
         * Read accelerometer
         * ------------------------- */

        if (icm20608_get_accel(
                sensor,
                &ax,
                &ay,
                &az) != RT_EOK)
        {
            rt_kprintf(
                "ERROR: accelerometer read failed\n"
            );

            rt_thread_mdelay(1000);

            continue;
        }


        /* -------------------------
         * Read gyroscope
         * ------------------------- */

        if (icm20608_get_gyro(
                sensor,
                &gx,
                &gy,
                &gz) != RT_EOK)
        {
            rt_kprintf(
                "ERROR: gyroscope read failed\n"
            );

            rt_thread_mdelay(1000);

            continue;
        }


        /* -------------------------
         * Build JSON payload
         *
         * app.py expects:
         * ax ay az gx gy gz
         * ------------------------- */

        rt_snprintf(
            payload,
            sizeof(payload),

            "{\"ax\":%d,"
            "\"ay\":%d,"
            "\"az\":%d,"
            "\"gx\":%d,"
            "\"gy\":%d,"
            "\"gz\":%d}",

            ax,
            ay,
            az,

            gx,
            gy,
            gz
        );


        rt_kprintf(
            "Publishing: %s\n",
            payload
        );


        /* -------------------------
         * Publish to Mosquitto
         * ------------------------- */

        if (mqtt_publish(
                mqtt_socket,
                payload) < 0)
        {
            rt_kprintf(
                "MQTT: publish failed\n"
            );

            lwip_close(
                mqtt_socket
            );

            mqtt_socket = -1;


            rt_thread_mdelay(1000);

            continue;
        }


        rt_kprintf(
            "MQTT: published successfully\n"
        );


        /*
         * Lab requires approximately
         * one reading per second.
         */
        rt_thread_mdelay(1000);
    }


    return 0;
}
