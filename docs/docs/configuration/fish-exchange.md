---
title: Native Fish Barter
---

The optional `fish-exchange.yml` catalog replaces `/emf shop` with a 36-slot barter menu. It is inactive when the file is missing or `enabled` is false. It does not enable Simulator or its reward bridge.

Each offer names an existing rarity, a required number of fish, and one native output: a LeDatPlatform MMOItems material or a physical LeDatExcellentCrates key. Fish must contain native EMF name/rarity NBT and resolve to an enabled registered fish; display names and lore cannot authorize a trade. Different sizes of the same rarity remain eligible.

Before consumption, the provider must create and identify the real output item. Physical keys are obtained from the crate provider's `getItemStack()` and validated through its key manager, preserving its PDC. Virtual keys and unavailable or mismatched providers fail closed.

The server plans the complete inventory outcome, including consumed inputs, existing stacks, and newly freed slots. Insufficient fish or space leaves the inventory unchanged. One owner-thread storage-inventory replacement commits the exchange; there are no Vault transactions, reward commands, overflow drops, or partial deliveries. The menu is read-only, belongs to one player/runtime, expires after three minutes, and closes on plugin disable. Offers have a one-second click cooldown.

`emf.shop` remains required. A regular plugin/server restart loads catalog changes. Existing cash selling remains available on installations without an enabled barter catalog. On a barter server, disable the configured economy types and point the main menu shop button at the player command `emf shop`. AutoFish currency purchases are unavailable while barter mode is enabled; stored AutoFish time, levels, and player records are preserved. Bait purchases require an available selected economy. Disabled economy selling cannot consume selected fish.

Catalog fields:

```yaml
enabled: true
title: '<aqua>Đổi Cá'
trades:
  common-material:
    slot: 10
    rarity: Common
    required: 1
    output:
      provider: MMOITEMS
      id: BP_TRADE_1
      amount: 8
    icon: COD
    name: '<aqua>Cá Thường</aqua>'
    lore:
      - ' '
      - '<dark_gray> ○ <white>Yêu cầu: <yellow>1 cá Thường</yellow>'
      - '<dark_gray> ○ <white>Nhận: <yellow>8 nguyên liệu khai thác</yellow>'
      - ' '
      - '<#A8D8EA>Nhấn chuột trái để đổi cá'
      - '<white>Cần đủ chỗ trống trong túi đồ.'
```

`MMOITEMS` outputs use namespace `MATERIAL`; `CRATE_KEY` outputs use an existing physical key ID. Amounts are positive and capped at 2,304. At most 14 offers are accepted; offer slots are within the two collection rows, and slot 31 is the close button. Invalid enabled catalogs keep the shop unavailable rather than falling back to cash selling. Existing inventories, fish metadata, journal/history, and crate key definitions are never migrated by this feature.
