# Design Rules for Nebula UI Engineering

Before touching any UI:
1. **Read DESIGN.md and the Design Skills**: Check `.agent/skills/` (`frontend-design`, `ui-ux-pro-max`, `sleek-design-mobile-apps`, `web-design-guidelines`, `design-taste-frontend`, `ui-animation`, `uxui-evaluator`) before creating or refactoring any Compose screen.
2. **Use Only Design Tokens**: Never use magic numbers, raw hardcoded hex colors, or ad-hoc dp padding outside the unified design token system (`NebulaTheme`, `NebulaColors`, `NebulaTypography`, `NebulaSpacing`, `NebulaShapes`).
3. **No Functional Regressions**: Keep all proxy connection logic, routing rules, data layer, encryption, and Keystore storage untouched. Every existing unit test must pass after each phase.
4. **Screenshot Verification**: Verify every visual change with direct emulator screencaps across all 3 themes, 3 locales (ru, de, zh-CN), and font scales (1.0, 1.3).
5. **Accessibility Standards**: Enforce WCAG AA contrast (>= 4.5:1), minimum 48dp touch targets, TalkBack content descriptions, and `LocalReduceMotion` compliance.
