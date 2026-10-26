package com.mclauncher.core

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

/** 网络小工具：本机地址、端口监听、UPnP 网关探测。 */
object NetTools {

    /** 取一个最像"局域网地址"的 IPv4 */
    fun localIPv4(): String? {
        return runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .asSequence()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                .flatMap { it.inetAddresses.toList().asSequence() }
                .filterIsInstance<java.net.Inet4Address>()
                .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                .mapNotNull { it.hostAddress }
                .firstOrNull()
        }.getOrNull()
    }

    /** 本机某个端口是否已经在监听（有服务开着） */
    fun isPortListening(port: Int, host: String = "127.0.0.1"): Boolean = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), 350)
            true
        }
    }.getOrDefault(false)

    /** SSDP 找一下路由器有没有开 UPnP，开了就能做端口映射 */
    fun upnpAvailable(timeoutMs: Int = 1200): Boolean {
        return runCatching {
            DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMs
                val message = "M-SEARCH * HTTP/1.1\r\n" +
                    "HOST: 239.255.255.250:1900\r\n" +
                    "MAN: \"ssdp:discover\"\r\n" +
                    "MX: 2\r\n" +
                    "ST: urn:schemas-upnp-org:device:InternetGatewayDevice:1\r\n\r\n"
                val payload = message.toByteArray()
                socket.send(
                    DatagramPacket(
                        payload, payload.size,
                        InetAddress.getByName("239.255.255.250"), 1900
                    )
                )
                val buffer = ByteArray(2048)
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)
                val text = String(buffer, 0, packet.length)
                text.contains("InternetGatewayDevice", true) || text.contains("200 OK", true)
            }
        }.getOrDefault(false)
    }

    data class LanWorld(val motd: String, val port: Int, val address: String)

    /**
     * 监听 Minecraft 的局域网广播（224.0.2.60:4445），
     * 把"对局域网开放"的世界找出来 —— 省得手动抄 IP。
     */
    fun discoverLanWorlds(timeoutMs: Int = 1500): List<LanWorld> {
        val found = LinkedHashMap<String, LanWorld>()
        runCatching {
            val group = InetAddress.getByName("224.0.2.60")
            val socket = java.net.MulticastSocket(4445)
            socket.joinGroup(java.net.InetSocketAddress(group, 4445), null)
            socket.soTimeout = 400
            val deadline = System.currentTimeMillis() + timeoutMs
            val buffer = ByteArray(2048)
            while (System.currentTimeMillis() < deadline) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (t: Throwable) {
                    continue
                }
                val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                val motd = Regex("\\[MOTD\\](.*?)\\[/MOTD\\]").find(text)?.groupValues?.get(1) ?: continue
                val port = Regex("\\[AD\\](\\d+)\\[/AD\\]").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: continue
                val addr = packet.address?.hostAddress ?: continue
                found["$addr:$port"] = LanWorld(motd.trim(), port, addr)
            }
            runCatching { socket.leaveGroup(java.net.InetSocketAddress(group, 4445), null) }
            socket.close()
        }
        return found.values.toList()
    }
}