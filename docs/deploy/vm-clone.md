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

本机 `vmrun` 路径：

```text
D:\Program Files (x86)\VMware\VMware Workstation\vmrun.exe
```

克隆前先关闭源虚拟机，然后执行：

```powershell
.\scripts\clone-vms.ps1
```

克隆后分别修改主机名和静态 IP。
