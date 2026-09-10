# ORACLE SPHERE

Original Android entertainment prototype: an obsidian-sapphire fortune sphere containing a viscous nebula and a physical twelve-faced oracle.

## Physics model

The runtime uses a reduced-order, incompressible viscous model. Shell acceleration excites three lagged slosh/vorticity modes. Fluid velocity follows the shell with a viscosity-dependent delay; die angular velocity follows the fluid through added-mass lag and Stokes-like drag. The shared decay time is `tau = 1.28 * rho / mu`, clamped for stability. Visual dye, particles, physical settling, and the haptic dissipation envelope all consume this same state.

The committed reading is the dodecahedron face normal with maximum camera-axis projection after motion falls below threshold. No post-settle RNG is used.

## Build

`gradle assembleDebug bundleRelease`

GitHub Actions uploads both the installable debug APK and release AAB.

