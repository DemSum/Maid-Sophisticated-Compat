# Experience Gap Implementation Plan

## Scope

Add the `wireless_io_exp` bauble without patching dependency JARs or adding direct compile-time dependencies on optional mods. Reuse the Touhou Little Maid Wireless IO layout and omit item filters, blacklist settings, and maid slot configuration.

## Module 1: Core Transfer

Status: source implementation complete; compilation and runtime validation pending.

Files:

- `WirelessIoExpBauble.java`

Requirements:

- Bind only block fluid handlers containing or clearly accepting a supported experience fluid.
- Run every 100 ticks while the maid GUI is closed.
- Require the same dimension, a loaded chunk, and the maid work-radius check.
- Support `create_enchantment_industry:experience` at 1 mB per XP.
- Support `sophisticatedcore:xp_still` at 20 mB per XP.
- Deposit only experience above the configured retained maid level.
- Simulate before execution and update maid experience from the actual executed amount.

## Module 2: Configuration UI

Status: source implementation complete; compilation and runtime validation pending.

Files:

- `WirelessIoExpBauble.java`
- `MaidSophiCompat.java`
- `en_us.json`
- `zh_cn.json`

Requirements:

- Open the configuration screen by using the item in the main hand.
- Keep the original Wireless IO screen size, background, inventory positions, and direction button.
- Show only the direction control and a nonnegative retained-level input.
- Synchronize settings through the vanilla container menu protocol without a custom payload.
- Add bilingual tooltips and interface text.

## Module 3: Validation And Release Update

Status: build complete; manual runtime validation and release update pending.

Files may include:

- `gradle.properties`
- `README.md`
- `CHANGELOG.md`

Requirements:

- Run `compileJava` only after confirmation.
- Test binding, both directions, full and empty containers, retained levels, range, dimensions, and unloaded chunks.
- Build and inspect the distributable JAR after confirmation.
- Update the version and bilingual release documentation after behavior is verified.

## Fixed Exclusions

- No filter, blacklist, or maid inventory-slot settings.
- No cross-dimensional transfer or chunk loading.
- No player experience handling.
- No arbitrary unknown experience fluids.
- No mixins, dependency JAR modifications, or copied dependency resources.
- No additional Java source files unless a verified platform restriction makes one necessary.
