fn main() {
    uniffi::generate_scaffolding("src/coolapk_core.udl").expect("generate UniFFI scaffolding");
}
