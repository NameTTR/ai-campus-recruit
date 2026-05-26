# VMware 克隆计划

源虚拟机目录：

```text
D:\Virtual_Machines\Ubuntu18_64_2
```

目标目录：

```text
D:\Virtual_Machines\ai-recruit-vm1
D:\Virtual_Machines\ai-recruit-vm2
D:\Virtual_Machines\ai-recruit-vm3
```

## vmrun 自动化

当前环境未检测到 `vmrun`，需要先把 VMware Workstation 安装目录加入 PATH。常见路径：

```text
C:\Program Files (x86)\VMware\VMware Workstation\vmrun.exe
```

克隆前先关闭源虚拟机，然后执行：

```powershell
vmrun clone "D:\Virtual_Machines\Ubuntu18_64_2\Ubuntu 64 位.vmx" "D:\Virtual_Machines\ai-recruit-vm1\ai-recruit-vm1.vmx" full -cloneName=ai-recruit-vm1
vmrun clone "D:\Virtual_Machines\Ubuntu18_64_2\Ubuntu 64 位.vmx" "D:\Virtual_Machines\ai-recruit-vm2\ai-recruit-vm2.vmx" full -cloneName=ai-recruit-vm2
vmrun clone "D:\Virtual_Machines\Ubuntu18_64_2\Ubuntu 64 位.vmx" "D:\Virtual_Machines\ai-recruit-vm3\ai-recruit-vm3.vmx" full -cloneName=ai-recruit-vm3
```

克隆后分别修改主机名和静态 IP。

