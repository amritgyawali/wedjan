import { Link, router } from "expo-router";
import { useState } from "react";
import {
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  Text,
  TextInput,
  View,
} from "react-native";
import { apiErrorMessage } from "@wedjan/shared";
import { api } from "@/lib/api";
import { formStyles as s } from "@/lib/form-styles";

const ROLE_OPTIONS = [
  { role: "CUSTOMER", title: "Plan an event", body: "Find and book trusted vendors" },
  { role: "VENDOR", title: "I'm a vendor", body: "Sell priced packages, grow your business" },
  { role: "FREELANCER", title: "I work events", body: "Pick up paid shifts from vendors" },
] as const;

type SignupRole = (typeof ROLE_OPTIONS)[number]["role"];

export default function SignupScreen() {
  const [role, setRole] = useState<SignupRole>("CUSTOMER");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit() {
    setError(null);
    setSubmitting(true);
    try {
      const { response, error: body } = await api.POST("/api/v1/auth/signup", {
        body: { email: email.trim(), password, role },
      });
      if (!response.ok) {
        setError(apiErrorMessage(body, "Signup failed — try again"));
        return;
      }
      router.push({ pathname: "/(auth)/verify", params: { email: email.trim() } });
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
        <Text style={s.title}>Create your account</Text>
        <Text style={s.subtitle}>
          Real prices, verified vendors, protected payments.
        </Text>

        {ROLE_OPTIONS.map((option) => (
          <Pressable
            key={option.role}
            style={[s.roleOption, role === option.role && s.roleOptionSelected]}
            onPress={() => setRole(option.role)}
          >
            <View style={{ flex: 1 }}>
              <Text style={s.roleTitle}>{option.title}</Text>
              <Text style={s.roleBody}>{option.body}</Text>
            </View>
          </Pressable>
        ))}

        <Text style={s.label}>Email</Text>
        <TextInput
          style={s.input}
          autoCapitalize="none"
          autoComplete="email"
          keyboardType="email-address"
          value={email}
          onChangeText={setEmail}
        />
        <Text style={s.label}>Password (min 10 characters)</Text>
        <TextInput
          style={s.input}
          secureTextEntry
          autoComplete="password-new"
          value={password}
          onChangeText={setPassword}
        />
        {error && <Text style={s.error}>{error}</Text>}
        <Pressable
          style={[s.primaryButton, submitting && s.primaryButtonDisabled]}
          disabled={submitting}
          onPress={onSubmit}
        >
          <Text style={s.primaryButtonText}>
            {submitting ? "Creating account…" : "Create account"}
          </Text>
        </Pressable>

        <Link href="/(auth)/login" asChild>
          <Pressable style={s.linkRow}>
            <Text style={s.mutedText}>
              Already have an account? <Text style={s.linkText}>Log in</Text>
            </Text>
          </Pressable>
        </Link>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}
