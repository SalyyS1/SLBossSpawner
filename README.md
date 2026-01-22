# FluffyBossSpawner

ปลักอิน Boss Spawner สำหรับ Minecraft ที่ทำงานร่วมกับ MythicMobs โดยมีระบบกำหนดเวลาเกิดและลบบอสอัตโนมัติ

## คำสั่ง

| คำสั่ง | Aliases | คำอธิบาย | Permission |
|--------|---------|----------|------------|
| `/fboss reload` | `/fluffyboss`, `/boss` | โหลดคอนฟิกใหม่ | `fluffyboss.reload` |
| `/fboss spawn [boss_id]` | - | เรียกบอส (ทั้งหมดหรือเฉพาะตัว) | `fluffyboss.spawn` |
| `/fboss despawn [boss_id]` | - | ลบบอส (ทั้งหมดหรือเฉพาะตัว) | `fluffyboss.despawn` |
| `/fboss info` | - | ดูข้อมูลบอสทั้งหมด | `fluffyboss.info` |
| `/fboss list` | - | แสดงรายชื่อบอส | `fluffyboss.info` |

## Permissions

```yaml
fluffyboss.use          
fluffyboss.reload       
fluffyboss.spawn        
fluffyboss.despawn      
fluffyboss.info         
fluffyboss.*           
```

## PlaceholderAPI

| Placeholder | คำอธิบาย | ตัวอย่าง |
|-------------|----------|----------|
| `%fbs_<boss_id>_current_boss%` | ชื่อบอสปัจจุบัน | `SkeletonKing` |
| `%fbs_<boss_id>_next%` | เวลาที่เหลือจนถึงบอสตัวถัดไป | `59:30` |
| `%fbs_<boss_id>_next_formatted%` | เวลาที่บอสตัวถัดไปจะเกิด | `18:00` |
| `%fbs_<boss_id>_expired%` | เวลาที่เหลือก่อนบอสหมดอายุ | `45:20` |
| `%fbs_<boss_id>_status%` | สถานะบอส | `Alive` หรือ `Dead` |
| `%fbs_<boss_id>_mythicmob%` | ชื่อ MythicMob | `SkeletonKing` |
| `%fbs_count%` | จำนวนบอสทั้งหมด | `3` |

---

Made with ❤️ by FluffyWorld
