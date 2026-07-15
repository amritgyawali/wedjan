import { StyleSheet } from "react-native";
import { tokens } from "@wedjan/ui-tokens";

/** Shared auth/form styles — one visual language across mobile screens. */
export const formStyles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: tokens.colors.background,
    padding: 24,
    paddingTop: 80,
  },
  wordmark: {
    color: tokens.colors.brandPink,
    fontSize: 28,
    fontWeight: "700",
    marginBottom: 24,
  },
  title: {
    color: tokens.colors.onSurface,
    fontSize: 28,
    fontWeight: "700",
    marginBottom: 8,
  },
  subtitle: {
    color: tokens.colors.secondary,
    fontSize: 14,
    lineHeight: 20,
    marginBottom: 24,
  },
  label: {
    color: tokens.colors.onSurface,
    fontSize: 14,
    fontWeight: "600",
    marginBottom: 8,
  },
  input: {
    minHeight: 50,
    borderWidth: 1,
    borderColor: "#d9d9d9",
    borderRadius: tokens.radii.md,
    paddingHorizontal: 14,
    backgroundColor: tokens.colors.surface,
    color: tokens.colors.onSurface,
    fontSize: 16,
    marginBottom: 16,
  },
  otpInput: {
    textAlign: "center",
    fontSize: 22,
    fontWeight: "700",
    letterSpacing: 12,
  },
  error: {
    borderWidth: 1,
    borderColor: "rgba(179, 38, 30, 0.25)",
    borderRadius: tokens.radii.md,
    padding: 12,
    backgroundColor: tokens.colors.dangerSoft,
    color: tokens.colors.danger,
    fontSize: 14,
    marginBottom: 16,
    overflow: "hidden",
  },
  primaryButton: {
    minHeight: 52,
    alignItems: "center",
    justifyContent: "center",
    borderRadius: tokens.radii.pill,
    backgroundColor: tokens.colors.brandPink,
    marginTop: 4,
  },
  primaryButtonDisabled: {
    opacity: 0.6,
  },
  primaryButtonText: {
    color: "#fff",
    fontSize: 16,
    fontWeight: "700",
  },
  linkRow: {
    marginTop: 20,
    alignItems: "center",
  },
  linkText: {
    color: tokens.colors.brandPink,
    fontSize: 14,
    fontWeight: "700",
    padding: 8,
  },
  mutedText: {
    color: tokens.colors.secondary,
    fontSize: 14,
  },
  roleOption: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    borderWidth: 1.5,
    borderColor: tokens.colors.surfaceVariant,
    borderRadius: tokens.radii.lg,
    padding: 14,
    backgroundColor: tokens.colors.surface,
    marginBottom: 10,
  },
  roleOptionSelected: {
    borderColor: tokens.colors.brandPink,
    backgroundColor: tokens.colors.pinkSoft,
  },
  roleTitle: {
    color: tokens.colors.onSurface,
    fontSize: 14,
    fontWeight: "700",
  },
  roleBody: {
    color: tokens.colors.secondary,
    fontSize: 13,
  },
});
