package com.breakingfree.app.lattice

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay

/** The Lattice's current mood, refreshed every 30 seconds. */
@Composable
fun rememberLatticeMood(): LatticeMood = produceLatticeMood().value

@Composable
private fun produceLatticeMood(): State<LatticeMood> = produceState(LatticeMoods.now()) {
    while (true) {
        delay(30_000)
        value = LatticeMoods.now()
    }
}
