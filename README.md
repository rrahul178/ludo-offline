# 🎲 Ludo Offline (Hotspot Multiplayer)

একটি সাধারণ Android Ludo গেম যা **ইন্টারনেট ছাড়াই** শুধুমাত্র **Wi-Fi Hotspot** ব্যবহার করে ২-৪ জন প্লেয়ার একসাথে খেলতে পারবে।

## কীভাবে কাজ করে

- একজন প্লেয়ার তার ফোনের **Hotspot ON** করবে এবং অ্যাপে **"Host Game"** চাপবে। তার ফোন একটি local server (TCP socket, port `8988`) চালু করবে।
- অন্য প্লেয়াররা সেই Hotspot-এ Wi-Fi দিয়ে কানেক্ট করবে এবং অ্যাপে **"Join Game"** চেপে Host-এর IP address (সাধারণত `192.168.43.1`) লিখে কানেক্ট করবে।
- Host একাই গেমের নিয়ম-কানুন (dice, move, capture, win) নিয়ন্ত্রণ করে এবং সবার সাথে state sync করে।

## প্রজেক্ট বিল্ড করার নিয়ম

### Android Studio দিয়ে
1. এই পুরো ফোল্ডার Android Studio-তে **Open** করুন।
2. Gradle sync হতে দিন।
3. একটি ডিভাইস/এমুলেটরে **Run** করুন।

### Command line দিয়ে (APK বানানোর জন্য)
```bash
gradle assembleDebug
```
APK পাবেন: `app/build/outputs/apk/debug/app-debug.apk`

## GitHub-এ আপলোড করার নিয়ম

```bash
cd LudoGame
git init
git add .
git commit -m "Initial commit - Ludo Offline hotspot multiplayer"
git branch -M main
git remote add origin https://github.com/<your-username>/<repo-name>.git
git push -u origin main
```

এই রিপোতে একটি **GitHub Actions workflow** (`.github/workflows/android.yml`) দেওয়া আছে — push করার পর Actions tab-এ গেলে এটি স্বয়ংক্রিয়ভাবে debug APK বিল্ড করবে এবং Artifacts থেকে download করা যাবে।

## খেলার নিয়ম (সংক্ষেপে)
- প্রতি প্লেয়ারের ৪টি টোকেন আছে।
- Dice-এ ৬ পড়লে yard থেকে টোকেন বের হয়।
- ৬ পড়লে / অপোনেন্টের টোকেন কাটলে / টোকেন ঘরে পৌঁছালে — আবার চাল পাবেন (extra turn)।
- নিরাপদ ঘর (হলুদ রঙের ★ ঘর) এ অন্য টোকেন কাটা যায় না।
- সবগুলো টোকেন ঘরে (center) পৌঁছালে সেই প্লেয়ার জয়ী।

## কাস্টমাইজেশন
- `GameLogic.kt` — বোর্ডের পথ, নিয়ম
- `LudoBoardView.kt` — বোর্ড আঁকা, রং
- `NetworkManager.kt` — TCP socket networking
- `GameActivity.kt` — গেম কন্ট্রোলার / turn লজিক
- `MainActivity.kt` — Host/Join মেনু
