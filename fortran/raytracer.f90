! CalBot Raytracer — Fortran 90
!
! A language invented in 1957 for scientific computing on IBM mainframes
! is being used here to raytrace a 3D sphere every time someone presses
! "=" on a calculator app running on an Android phone.
!
! The sphere's color is derived from the calculator result.
! The image is 64x64 pixels, rendered in PPM format.
!
! Also computes eigenvalues via QR iteration and SVD of a 4x4 matrix
! because pressing "=" should involve linear algebra.
!
! Compile: gfortran -shared -fPIC -O2 -o libcalbot_fortran.so raytracer.f90
! Test:    gfortran -O2 -o test_raytrace raytracer.f90 test_main.f90 && ./test_raytrace

module calbot_raytracer
    implicit none
    integer, parameter :: IMG_W = 64, IMG_H = 64

    ! Sphere definition
    real(8), parameter :: SPHERE_X = 0.0d0
    real(8), parameter :: SPHERE_Y = 0.0d0
    real(8), parameter :: SPHERE_Z = 3.0d0
    real(8), parameter :: SPHERE_R = 1.0d0

    ! Light direction (normalized)
    real(8), parameter :: LIGHT_X = -0.5773502691896258d0
    real(8), parameter :: LIGHT_Y = -0.5773502691896258d0
    real(8), parameter :: LIGHT_Z = -0.5773502691896258d0

    ! Pixel buffer
    integer :: framebuffer(3, IMG_W, IMG_H)

contains

    ! ----------------------------------------------------------------
    ! Ray-sphere intersection
    ! Returns distance t, or -1 if no hit
    ! ----------------------------------------------------------------
    function ray_sphere_intersect(ox, oy, oz, dx, dy, dz) result(t)
        real(8), intent(in) :: ox, oy, oz, dx, dy, dz
        real(8) :: t
        real(8) :: cx, cy, cz, a, b, c_val, disc, t0, t1

        cx = ox - SPHERE_X
        cy = oy - SPHERE_Y
        cz = oz - SPHERE_Z

        a = dx*dx + dy*dy + dz*dz
        b = 2.0d0 * (cx*dx + cy*dy + cz*dz)
        c_val = cx*cx + cy*cy + cz*cz - SPHERE_R*SPHERE_R

        disc = b*b - 4.0d0*a*c_val

        if (disc < 0.0d0) then
            t = -1.0d0
            return
        end if

        t0 = (-b - sqrt(disc)) / (2.0d0 * a)
        t1 = (-b + sqrt(disc)) / (2.0d0 * a)

        if (t0 > 0.001d0) then
            t = t0
        else if (t1 > 0.001d0) then
            t = t1
        else
            t = -1.0d0
        end if
    end function

    ! ----------------------------------------------------------------
    ! Render the sphere into the framebuffer
    ! Color is derived from the calculator result
    ! ----------------------------------------------------------------
    subroutine render_sphere(calc_result)
        integer, intent(in) :: calc_result
        integer :: ix, iy
        real(8) :: u, v, dx, dy, dz, dn, t
        real(8) :: hx, hy, hz, nx, ny, nz, diff
        real(8) :: base_r, base_g, base_b
        integer :: r, g, b

        ! Derive sphere color from calculator result
        base_r = mod(abs(calc_result) * 137, 256) / 255.0d0
        base_g = mod(abs(calc_result) * 59,  256) / 255.0d0
        base_b = mod(abs(calc_result) * 97,  256) / 255.0d0

        ! Ensure it's not too dark
        if (base_r + base_g + base_b < 0.5d0) then
            base_r = base_r + 0.3d0
            base_g = base_g + 0.2d0
            base_b = base_b + 0.4d0
        end if

        do iy = 1, IMG_H
            do ix = 1, IMG_W
                ! Map pixel to normalized device coords [-1, 1]
                u = (2.0d0 * ix / IMG_W - 1.0d0)
                v = (2.0d0 * iy / IMG_H - 1.0d0)

                ! Ray direction (simple pinhole camera)
                dx = u
                dy = v
                dz = 1.5d0
                dn = sqrt(dx*dx + dy*dy + dz*dz)
                dx = dx / dn
                dy = dy / dn
                dz = dz / dn

                ! Test intersection
                t = ray_sphere_intersect(0.0d0, 0.0d0, 0.0d0, dx, dy, dz)

                if (t > 0.0d0) then
                    ! Hit point
                    hx = dx * t
                    hy = dy * t
                    hz = dz * t

                    ! Normal at hit point
                    nx = (hx - SPHERE_X) / SPHERE_R
                    ny = (hy - SPHERE_Y) / SPHERE_R
                    nz = (hz - SPHERE_Z) / SPHERE_R

                    ! Lambertian diffuse shading
                    diff = max(0.0d0, -(nx*LIGHT_X + ny*LIGHT_Y + nz*LIGHT_Z))
                    diff = 0.15d0 + 0.85d0 * diff  ! ambient + diffuse

                    r = min(255, int(base_r * diff * 255.0d0))
                    g = min(255, int(base_g * diff * 255.0d0))
                    b = min(255, int(base_b * diff * 255.0d0))

                    framebuffer(1, ix, iy) = r
                    framebuffer(2, ix, iy) = g
                    framebuffer(3, ix, iy) = b
                else
                    ! Background — dark vaporwave gradient
                    r = int(15.0d0 + 10.0d0 * real(iy) / IMG_H)
                    g = int(2.0d0  + 5.0d0  * real(iy) / IMG_H)
                    b = int(33.0d0 + 20.0d0 * real(iy) / IMG_H)

                    framebuffer(1, ix, iy) = r
                    framebuffer(2, ix, iy) = g
                    framebuffer(3, ix, iy) = b
                end if
            end do
        end do
    end subroutine

    ! ----------------------------------------------------------------
    ! Useless 4x4 eigenvalue computation via QR iteration
    ! Because pressing "=" should involve linear algebra
    ! ----------------------------------------------------------------
    subroutine useless_eigen(calc_result, eigenvalues)
        integer, intent(in) :: calc_result
        real(8), intent(out) :: eigenvalues(4)
        real(8) :: A(4,4), Q(4,4), R(4,4), temp(4,4)
        integer :: i, j, k, iter
        real(8) :: norm_val, dot_val, seed

        ! Build a symmetric matrix from the calculator result
        seed = real(calc_result, 8)
        do i = 1, 4
            do j = i, 4
                A(i,j) = sin(seed * i * 0.7d0 + j * 1.3d0) * 10.0d0
                A(j,i) = A(i,j)  ! symmetric
            end do
        end do

        ! QR iteration (30 iterations of Gram-Schmidt QR decomposition)
        ! This is deliberately unoptimized. LAPACK would do it in microseconds.
        ! We do it the hard way because this is a calculator app.
        do iter = 1, 30
            ! Gram-Schmidt QR decomposition
            Q = 0.0d0
            R = 0.0d0

            do j = 1, 4
                ! Start with column j of A
                do i = 1, 4
                    Q(i,j) = A(i,j)
                end do

                ! Subtract projections onto previous columns
                do k = 1, j-1
                    dot_val = 0.0d0
                    do i = 1, 4
                        dot_val = dot_val + Q(i,k) * A(i,j)
                    end do
                    R(k,j) = dot_val
                    do i = 1, 4
                        Q(i,j) = Q(i,j) - dot_val * Q(i,k)
                    end do
                end do

                ! Normalize
                norm_val = 0.0d0
                do i = 1, 4
                    norm_val = norm_val + Q(i,j) * Q(i,j)
                end do
                norm_val = sqrt(norm_val)
                R(j,j) = norm_val
                if (norm_val > 1.0d-12) then
                    do i = 1, 4
                        Q(i,j) = Q(i,j) / norm_val
                    end do
                end if
            end do

            ! A = R * Q for next iteration
            temp = 0.0d0
            do i = 1, 4
                do j = 1, 4
                    do k = 1, 4
                        temp(i,j) = temp(i,j) + R(i,k) * Q(k,j)
                    end do
                end do
            end do
            A = temp
        end do

        ! Eigenvalues are on the diagonal after convergence
        do i = 1, 4
            eigenvalues(i) = A(i,i)
        end do
    end subroutine

    ! ----------------------------------------------------------------
    ! Useless 4x4 SVD via eigendecomposition of A^T * A
    ! Singular values = sqrt(eigenvalues of A^T * A)
    ! ----------------------------------------------------------------
    subroutine useless_svd(calc_result, singular_values)
        integer, intent(in) :: calc_result
        real(8), intent(out) :: singular_values(4)
        real(8) :: A(4,4), ATA(4,4), eigenvalues(4)
        integer :: i, j, k
        real(8) :: seed

        ! Build a matrix from the calculator result
        seed = real(calc_result, 8)
        do i = 1, 4
            do j = 1, 4
                A(i,j) = cos(seed * i * 0.3d0 + j * 2.1d0) * 5.0d0 + &
                          sin(seed * (i+j) * 0.9d0) * 3.0d0
            end do
        end do

        ! Compute A^T * A
        ATA = 0.0d0
        do i = 1, 4
            do j = 1, 4
                do k = 1, 4
                    ATA(i,j) = ATA(i,j) + A(k,i) * A(k,j)
                end do
            end do
        end do

        ! Eigenvalues of A^T * A via QR iteration
        call useless_eigen_raw(ATA, eigenvalues)

        ! Singular values = sqrt of eigenvalues (clamped to >= 0)
        do i = 1, 4
            if (eigenvalues(i) > 0.0d0) then
                singular_values(i) = sqrt(eigenvalues(i))
            else
                singular_values(i) = 0.0d0
            end if
        end do
    end subroutine

    ! Raw QR iteration on a provided matrix (for SVD)
    subroutine useless_eigen_raw(A, eigenvalues)
        real(8), intent(inout) :: A(4,4)
        real(8), intent(out) :: eigenvalues(4)
        real(8) :: Q(4,4), R(4,4), temp(4,4)
        integer :: i, j, k, iter
        real(8) :: norm_val, dot_val

        do iter = 1, 30
            Q = 0.0d0
            R = 0.0d0
            do j = 1, 4
                do i = 1, 4
                    Q(i,j) = A(i,j)
                end do
                do k = 1, j-1
                    dot_val = 0.0d0
                    do i = 1, 4
                        dot_val = dot_val + Q(i,k) * A(i,j)
                    end do
                    R(k,j) = dot_val
                    do i = 1, 4
                        Q(i,j) = Q(i,j) - dot_val * Q(i,k)
                    end do
                end do
                norm_val = 0.0d0
                do i = 1, 4
                    norm_val = norm_val + Q(i,j) * Q(i,j)
                end do
                norm_val = sqrt(norm_val)
                R(j,j) = norm_val
                if (norm_val > 1.0d-12) then
                    do i = 1, 4
                        Q(i,j) = Q(i,j) / norm_val
                    end do
                end if
            end do
            temp = 0.0d0
            do i = 1, 4
                do j = 1, 4
                    do k = 1, 4
                        temp(i,j) = temp(i,j) + R(i,k) * Q(k,j)
                    end do
                end do
            end do
            A = temp
        end do
        do i = 1, 4
            eigenvalues(i) = A(i,i)
        end do
    end subroutine

    ! ----------------------------------------------------------------
    ! Master entry point: raytrace + eigenvalues + SVD
    ! Called from JNI on every "=" press
    ! ----------------------------------------------------------------
    subroutine fortran_compute(calc_result, eigenvalues, singular_values, &
                               pixel_checksum) bind(C, name="fortran_compute")
        integer, intent(in) :: calc_result
        real(8), intent(out) :: eigenvalues(4)
        real(8), intent(out) :: singular_values(4)
        integer, intent(out) :: pixel_checksum
        integer :: ix, iy, checksum

        ! Phase 1: Raytrace a sphere whose color depends on the result
        call render_sphere(calc_result)

        ! Phase 2: Useless eigenvalue computation
        call useless_eigen(calc_result, eigenvalues)

        ! Phase 3: Useless SVD
        call useless_svd(calc_result, singular_values)

        ! Compute a checksum of the rendered image so the compiler
        ! can't optimize away the raytracing
        checksum = 0
        do iy = 1, IMG_H
            do ix = 1, IMG_W
                checksum = checksum + framebuffer(1,ix,iy) + &
                           framebuffer(2,ix,iy) + framebuffer(3,ix,iy)
            end do
        end do
        pixel_checksum = checksum
    end subroutine

    ! ----------------------------------------------------------------
    ! Export the framebuffer as a flat RGBA byte array for OpenGL
    ! Called from JNI after fortran_compute to feed the GL texture
    ! ----------------------------------------------------------------
    subroutine fortran_get_pixels(rgba_out) bind(C, name="fortran_get_pixels")
        integer(1), intent(out) :: rgba_out(4 * IMG_W * IMG_H)
        integer :: ix, iy, idx

        idx = 1
        do iy = 1, IMG_H
            do ix = 1, IMG_W
                rgba_out(idx)     = int(framebuffer(1, ix, iy), 1)  ! R
                rgba_out(idx + 1) = int(framebuffer(2, ix, iy), 1)  ! G
                rgba_out(idx + 2) = int(framebuffer(3, ix, iy), 1)  ! B
                rgba_out(idx + 3) = -1  ! A = 255 (signed byte)
                idx = idx + 4
            end do
        end do
    end subroutine

end module calbot_raytracer
