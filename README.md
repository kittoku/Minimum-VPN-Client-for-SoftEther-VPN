# Minimum VPN Client for SoftEther VPN
This is an open-source SoftEther-VPN-protocol-based VPN client for Android

## Notice
* "SoftEther" is a registered trademark of SoftEther Corporation
* This is an **unofficial** project. Don't ask about this app in the official forum or repository
* Currently there is **no advantage** of using this app, compared with other protocols like L2TP, OpenVPN or SSTP (see Limitation)

## What *Minimum* means?
I want to establish a VPN connection via [VPN Azure](http://www.vpnazure.net/en/)
with UDP acceleration enabled from Android. So this app's goal is to implement minimum features to do that.

## Limitation
You need to satisfy the following conditions to use this app.
* DHCP(SecureNAT) is enabled
* Password authentication is enabled

## Milestones
- [x] works with a global-IP-address-assigned server
- [x] works with VPN azure
- [x] works with a global-IP-address-assigned server + UDP acceleration
- [x] works with VPN azure + UDP acceleration

(Not confirmed in a situation where both a server and a client are inside LAN. Reports are welcome!)

## Installation
You can download the latest version APK
[here](https://github.com/kittoku/Minimum-VPN-Client-for-SoftEther-VPN/releases/download/v0.2.0/mvc-0.2.0.apk).

## Screenshots
<img src="images/example_home.png" width=25%> <img src="images/example_setting.png" width=25%>
