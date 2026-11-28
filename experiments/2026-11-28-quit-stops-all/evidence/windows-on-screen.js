ObjC.import("CoreGraphics");
const arr = ObjC.castRefToObject($.CGWindowListCopyWindowInfo($.kCGWindowListOptionOnScreenOnly, 0));
const out = [];
for (let i = 0; i < arr.count; i++) {
  const owner = ObjC.unwrap(arr.objectAtIndex(i).objectForKey("kCGWindowOwnerName"));
  if (/UserNotificationCenter|Nouride Home Dev/.test(owner)) out.push(owner);
}
console.log(out.length ? out.join("\n") : "no window of UserNotificationCenter or Nouride Home Dev on screen");
