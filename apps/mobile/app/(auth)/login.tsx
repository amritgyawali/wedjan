import { Link, router } from "expo-router";
import { useState } from "react";
import {
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  Text,
  TextInput,
} from "react-native";
import { useAuth } from "@/lib/auth-context";
import { formStyles as s } from "@/lib/form-styles";

export default function LoginScreen() {
  const { login } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit() {
    setError(null);
    setSubmitting(true);
    try {
      await login(email.trim(), password);
      router.replace("/(app)/home");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Login failed");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <KeyboardAvoidingView
      style={{ flex: 1 }}
      behavior={Platform.OS === "ios" ? "padding" : undefined}
    >
      <ScrollView style={s.screen} keyboardShouldPersistTaps="handled">
        <Text style={s.wordmark}>wedjan</Text>
        <Text style={s.title}>Welcome back</Text>
        <Text style={s.subtitle}>Log in to keep planning, selling, or working events.</Text>

        <Text style={s.label}>Email</Text>
        <TextInput
          style={s.input}
          autoCapitalize="none"
          autoComplete="email"
          keyboardType="email-address"
          value={email}
          onChangeText={setEmail}
        />
        <Text style={s.label}>Password</Text>
        <TextInput
          style={s.input}
          secureTextEntry
          autoComplete="password"
          value={password}
          onChangeText={setPassword}
        />
        {error && <Text style={s.error}>{error}</Text>}
        <Pressable
          style={[s.primaryButton, submitting && s.primaryButtonDisabled]}
          disabled={submitting}
          onPress={onSubmit}
        >
          <Text style={s.primaryButtonText}>{submitting ? "Logging in…" : "Log in"}</Text>
        </Pressable>

        <Link href="/(auth)/signup" asChild>
          <Pressable style={s.linkRow}>
            <Text style={s.mutedText}>
              New to wedjan? <Text style={s.linkText}>Create an account</Text>
            </Text>
          </Pressable>
        </Link>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}
