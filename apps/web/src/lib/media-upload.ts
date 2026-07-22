import { api } from "./api";

export async function compressImage(file: File): Promise<File> {
  if (!file.type.startsWith("image/") || file.size < 1_000_000) return file;
  const bitmap = await createImageBitmap(file);
  const scale = Math.min(1, 2200 / Math.max(bitmap.width, bitmap.height));
  const canvas = document.createElement("canvas");
  canvas.width = Math.round(bitmap.width * scale);
  canvas.height = Math.round(bitmap.height * scale);
  canvas.getContext("2d")?.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
  const blob = await new Promise<Blob>((resolve, reject) =>
    canvas.toBlob((result) => (result ? resolve(result) : reject(new Error("Image compression failed"))), "image/jpeg", 0.84),
  );
  return new File([blob], file.name.replace(/\.[^.]+$/, ".jpg"), { type: "image/jpeg" });
}

export async function uploadMedia(file: File, kind: "IMAGE" | "VIDEO" | "DOC") {
  const prepared = kind === "IMAGE" ? await compressImage(file) : file;
  const { data: presign, error } = await api.POST("/api/v1/media/presign", {
    body: { kind, mime: prepared.type, bytes: prepared.size },
  });
  if (!presign) throw new Error(error?.error.message ?? "Could not prepare upload");
  const upload = await fetch(presign.uploadUrl, {
    method: "PUT",
    headers: presign.headers,
    body: prepared,
  });
  if (!upload.ok) throw new Error("File upload failed");
  const { data: complete, error: completeError } = await api.POST("/api/v1/media/{mediaId}/complete", {
    params: { path: { mediaId: presign.mediaId } },
  });
  if (!complete) throw new Error(completeError?.error.message ?? "Could not finish upload");
  return complete;
}
